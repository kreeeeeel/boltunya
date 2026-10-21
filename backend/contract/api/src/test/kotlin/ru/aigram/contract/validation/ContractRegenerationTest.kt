package ru.aigram.contract.validation

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractRegenerationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `regeneration removes obsolete model source and compiled class in an isolated project`() {
        val apiProject = Path.of(".").toAbsolutePath().normalize()
        val project = directory.resolve("fixture")
        val api = project.resolve("api")
        Files.createDirectories(api)
        Files.copy(apiProject.resolve("build.gradle.kts"), api.resolve("build.gradle.kts"))
        copyTree(apiProject.resolve("templates"), api.resolve("templates"))
        writeProject(project, apiProject.parent.parent.parent.resolve("build.gradle.kts"))
        writeSchema(api, "OldModel")

        val first = runner(project).build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":api:openApiGenerate")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, first.task(":api:compileKotlin")?.outcome)
        val sources = api.resolve("build/generated/openapi/src/main/kotlin/ru/aigram/contract/dto")
        val classes = api.resolve("build/classes/kotlin/main/ru/aigram/contract/dto")
        assertTrue(Files.isRegularFile(sources.resolve("OldModel.kt")))
        assertTrue(Files.isRegularFile(classes.resolve("OldModel.class")))
        assertTrue(Files.readString(sources.resolve("OldModel.kt")).contains("val state: FixtureState"))
        assertTrue(Files.readString(sources.resolve("FixtureState.kt")).contains("enum class FixtureState"))

        // Change only the fixture's canonical source tree, without cleaning or patching generated output.
        Files.delete(api.resolve("schema/models/OldModel.yaml"))
        writeSchema(api, "NewModel")
        val second = runner(project).build()
        assertEquals(TaskOutcome.SUCCESS, second.task(":api:openApiGenerate")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, second.task(":api:compileKotlin")?.outcome)
        assertFalse(Files.exists(sources.resolve("OldModel.kt")), "Obsolete generated Kotlin source remains")
        assertFalse(Files.exists(classes.resolve("OldModel.class")), "Obsolete compiled DTO remains")
        assertTrue(Files.isRegularFile(sources.resolve("NewModel.kt")))
        assertTrue(Files.isRegularFile(classes.resolve("NewModel.class")))
        assertTrue(Files.readString(sources.resolve("NewModel.kt")).contains("val state: FixtureState"))
    }

    private fun writeProject(project: Path, originalBuild: Path) {
        val source = Files.readString(originalBuild)
        val kotlinVersion = pluginVersion(source, "kotlin(\"jvm\") version \"")
        val generatorVersion = pluginVersion(source, "id(\"org.openapi.generator\") version \"")
        val ktlintVersion = pluginVersion(source, "id(\"org.jlleitschuh.gradle.ktlint\") version \"")
        Files.writeString(
            project.resolve("settings.gradle.kts"),
            "rootProject.name = \"contract-regeneration\"\ninclude(\":api\")\n",
        )
        Files.writeString(
            project.resolve("build.gradle.kts"),
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion" apply false
                id("org.openapi.generator") version "$generatorVersion" apply false
                id("org.jlleitschuh.gradle.ktlint") version "$ktlintVersion" apply false
            }
            """.trimIndent() + "\n",
        )
        val javaHome = System.getProperty("java.home").replace('\\', '/')
        Files.writeString(
            project.resolve("gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.daemon.idletimeout=10000
            org.gradle.workers.max=1
            org.gradle.java.home=$javaHome
            org.gradle.java.installations.auto-download=false
            kotlin.compiler.execution.strategy=in-process
            """.trimIndent() + "\n",
        )
    }

    private fun pluginVersion(build: String, marker: String): String {
        val start = build.indexOf(marker)
        if (start < 0) {
            throw IllegalStateException("Missing root plugin declaration: $marker")
        }
        val version = build.substring(start + marker.length).substringBefore('"')
        if (version.isBlank()) {
            throw IllegalStateException("Empty root plugin version: $marker")
        }
        return version
    }

    private fun runner(project: Path): GradleRunner {
        val installation = System.getProperty("aigram.test.gradleHome")
        if (installation.isNullOrBlank()) {
            throw IllegalStateException("The outer build must supply the actual Gradle installation")
        }
        val configuredCache = System.getenv("GRADLE_USER_HOME")
        val cache = if (configuredCache.isNullOrBlank()) {
            Path.of(System.getProperty("user.home"), ".gradle")
        } else {
            Path.of(configuredCache)
        }
        return GradleRunner.create()
            .withGradleInstallation(Path.of(installation).toFile())
            .withProjectDir(project.toFile())
            .withArguments(
                ":api:openApiGenerate",
                ":api:compileKotlin",
                "--gradle-user-home",
                cache.toString(),
                "--max-workers=1",
                "--no-build-cache",
                "--stacktrace",
            )
    }

    private fun copyTree(source: Path, target: Path) {
        if (!Files.isDirectory(source)) {
            throw IllegalStateException("The real contract templates directory is missing")
        }
        Files.walk(source).use { files ->
            files.filter { Files.isRegularFile(it) }.forEach { file ->
                val destination = target.resolve(source.relativize(file))
                Files.createDirectories(destination.parent)
                Files.copy(file, destination)
            }
        }
    }

    private fun writeSchema(api: Path, model: String) {
        val schema = api.resolve("schema")
        Files.createDirectories(schema.resolve("operations"))
        Files.createDirectories(schema.resolve("models"))
        Files.writeString(
            schema.resolve("api.yaml"),
            """
            openapi: 3.0.3
            info:
                title: Regeneration fixture
                version: 1.0.0
            paths:
                /fixture/model:
                    ${'$'}ref: ./operations/fixture.yaml#/model
            components:
                schemas:
                    $model:
                        ${'$'}ref: ./models/$model.yaml#/$model
                    FixtureState:
                        ${'$'}ref: ./models/FixtureState.yaml#/FixtureState
            """.trimIndent() + "\n",
        )
        Files.writeString(
            schema.resolve("operations/fixture.yaml"),
            """
            model:
                get:
                    operationId: readFixtureModel
                    tags:
                        - fixture
                    responses:
                        '200':
                            description: Typed fixture model
                            content:
                                application/json:
                                    schema:
                                        ${'$'}ref: ../models/$model.yaml#/$model
            """.trimIndent() + "\n",
        )
        Files.writeString(
            schema.resolve("models/$model.yaml"),
            """
            $model:
                type: object
                required:
                    - id
                    - state
                properties:
                    id:
                        type: string
                    state:
                        ${'$'}ref: ./FixtureState.yaml#/FixtureState
            """.trimIndent() + "\n",
        )
        Files.writeString(
            schema.resolve("models/FixtureState.yaml"),
            """
            FixtureState:
                type: string
                enum:
                    - ready
                    - paused
            """.trimIndent() + "\n",
        )
    }
}
