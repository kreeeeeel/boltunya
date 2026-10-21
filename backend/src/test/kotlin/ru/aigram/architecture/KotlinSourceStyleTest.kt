package ru.aigram.architecture

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinSourceStyleTest {
    @Test
    fun `handwritten Kotlin keeps control flow explicit and domain models grouped by feature`() {
        withParser { factory ->
            val roots = Stream.of(
                Path.of("src/main/kotlin"),
                Path.of("src/test/kotlin"),
                Path.of("contract/api/src/test/kotlin"),
                Path.of("contract/client/src/main/kotlin"),
                Path.of("contract/client/src/test/kotlin"),
            )
            val failures = roots.flatMap { root ->
                Files.walk(root).filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            }.use { files ->
                files.flatMap { path ->
                    val source = factory.createFile(path.fileName.toString(), normalize(Files.readString(path)))
                    violations(source).stream().map { "$path: $it" }
                }.toList()
            }

            assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        }
    }

    @Test
    fun `AST audit distinguishes executable conditions from comments and string examples`() {
        withParser { factory ->
            val invalid = factory.createFile("invalid.kt", "fun answer(value: Boolean) = if (value) 1 else 2")
            assertEquals(2, violations(invalid).size)

            val compact = factory.createFile("compact.kt", "fun answer(value: Boolean) { if (value) { return } }")
            assertEquals(1, violations(compact).size)

            val valid = factory.createFile(
                "valid.kt",
                """
                // Example: if (value) return
                fun answer(value: Boolean): String {
                    val example = "if (value) return"
                    if (value) {
                        return example
                    } else if (!value) {
                        return "no"
                    } else {
                        return "unknown"
                    }
                }
                """.trimIndent(),
            )
            assertTrue(violations(valid).isEmpty())
        }
    }

    @Test
    fun `Windows line separators and an optional utf8 bom parse exactly like unix source`() {
        withParser { factory ->
            val unix = "package example\n\nclass Example { fun answer() = 42 }\n"
            val windows = "\uFEFF" + unix.replace("\n", "\r\n")
            val expected = violations(factory.createFile("unix.kt", unix))
            val actual = violations(factory.createFile("windows.kt", normalize(windows)))

            assertTrue(expected.isEmpty())
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `architecture audit rejects obsolete HTTP packages and dependency injection imports`() {
        withParser { factory ->
            val obsolete = factory.createFile(
                "obsolete.kt",
                """
                package ru.aigram.http.endpoint
                import jakarta.inject.Inject
                import com.google.inject.Provider
                class Obsolete
                """.trimIndent(),
            )
            assertEquals(3, violations(obsolete).size)

            val current = factory.createFile(
                "current.kt",
                """
                package ru.aigram.component.example
                import org.springframework.stereotype.Component
                @Component
                class Current {
                    val example = "import jakarta.inject.Inject"
                }
                """.trimIndent(),
            )
            assertTrue(violations(current).isEmpty())
        }
    }

    @Test
    fun `one named class per file includes nested declarations while allowing framework callbacks`() {
        withParser { factory ->
            val nested = factory.createFile("nested.kt", "class Outer { class Inner }")
            assertEquals(1, violations(nested).size)

            val callback = factory.createFile(
                "callback.kt",
                "class Callback { val action = object : Runnable { override fun run() = Unit } }",
            )
            assertTrue(violations(callback).isEmpty())

            val enumeration = factory.createFile("state.kt", "enum class State { READY, FAILED }")
            assertTrue(violations(enumeration).isEmpty())
        }
    }

    private fun violations(file: KtFile): List<String> {
        val syntax = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).stream()
            .map { "Syntax error: ${it.errorDescription}" }
        val branches = PsiTreeUtil.collectElementsOfType(file, KtIfExpression::class.java).stream()
            .flatMap { expression ->
                Stream.of(expression.then, expression.`else`)
                    .filter { it != null && !(it is KtIfExpression && expression.`else` === it) }
                    .filter { it !is KtBlockExpression || !it.text.contains('\n') }
                    .map {
                        "Conditional branch requires a multiline braced body at offset ${it?.textRange?.startOffset}"
                    }
            }
        val prohibited = Arrays.stream(
            PsiTreeUtil.collectElements(file) {
                it is KtLoopExpression || it is KtAnonymousInitializer ||
                    it is KtPostfixExpression && it.operationToken == KtTokens.EXCLEXCL ||
                    it is KtBinaryExpression && it.operationToken == KtTokens.ELVIS && it.right is KtThrowExpression
            },
        ).map { "Implicit or prohibited control flow at offset ${it.textRange.startOffset}" }
        val packages = if (file.packageFqName.asString() == "ru.aigram.domain") {
            Stream.of("Domain models require a feature package")
        } else if (file.packageFqName.asString() == "ru.aigram.http" ||
            file.packageFqName.asString().startsWith("ru.aigram.http.")
        ) {
            Stream.of("Legacy HTTP packages are prohibited")
        } else {
            Stream.empty()
        }
        val imports = file.importDirectives.stream().map { it.importedFqName?.asString().orEmpty() }
            .filter { name ->
                Stream.of("ru.aigram.http", "jakarta.inject", "com.google.inject").anyMatch {
                    name == it || name.startsWith("$it.")
                }
            }.map { "Obsolete architecture import: $it" }
        val classCount = PsiTreeUtil.collectElementsOfType(file, KtClassOrObject::class.java).stream()
            .filter { it !is KtEnumEntry && it.name != null }.count()
        val declarations = if (classCount > 1) {
            Stream.of("Only one named class or interface is allowed per file, including nested declarations")
        } else {
            Stream.empty()
        }

        return Stream.of(syntax, branches, prohibited, packages, imports, declarations).flatMap { it }.toList()
    }

    private fun normalize(source: String): String = StringUtil.convertLineSeparators(source.removePrefix("\uFEFF"))

    @OptIn(K1Deprecation::class)
    private fun withParser(action: (KtPsiFactory) -> Unit) {
        val disposable = Disposer.newDisposable()
        try {
            val environment = KotlinCoreEnvironment.createForProduction(
                disposable,
                CompilerConfiguration(),
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
            action(KtPsiFactory(environment.project, false))
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
