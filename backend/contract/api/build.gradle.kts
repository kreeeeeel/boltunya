import org.gradle.api.tasks.PathSensitivity
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

plugins {
    kotlin("jvm")
    `java-library`
    id("org.openapi.generator")
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenCentral()
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    api("org.springframework:spring-webmvc")
    api("jakarta.validation:jakarta.validation-api")
    api("com.fasterxml.jackson.core:jackson-annotations")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.yaml:snakeyaml")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val schemaDirectory = layout.projectDirectory.dir("schema")
val schemaFiles = fileTree(schemaDirectory)
val generatedDirectory = layout.buildDirectory.dir("generated/openapi")

val bundledSchema = layout.buildDirectory.file("bundled/api.yaml")

val validateSource = tasks.register<ValidateTask>("validateSource") {
    inputSpec.set(schemaDirectory.file("api.yaml"))
    inputs.files(schemaFiles).withPropertyName("schemaTree").withPathSensitivity(PathSensitivity.RELATIVE)
}

val bundleContract = tasks.register("bundleContract") {
    dependsOn(validateSource)
    inputs.files(schemaFiles).withPropertyName("schemaTree").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(bundledSchema)
    doLast {
        val output = bundledSchema.get().asFile.toPath()
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
            splitLines = false
        }
        Files.createDirectories(output.parent)
        Files.writeString(output, Yaml(options).dump(bundleSchema(schemaDirectory.file("api.yaml").asFile.toPath())))
    }
}

tasks.named<ValidateTask>("openApiValidate") {
    dependsOn(bundleContract)
    inputSpec.set(bundledSchema)
    inputs.files(schemaFiles).withPropertyName("schemaTree").withPathSensitivity(PathSensitivity.RELATIVE)
}

val generateContract = tasks.named<GenerateTask>("openApiGenerate") {
    dependsOn(tasks.named("openApiValidate"))
    generatorName.set("kotlin-spring")
    inputSpec.set(bundledSchema)
    outputDir.set(generatedDirectory)
    templateDir.set(layout.projectDirectory.dir("templates"))
    apiPackage.set("ru.aigram.contract.api")
    modelPackage.set("ru.aigram.contract.dto")
    cleanupOutput.set(true)
    validateSpec.set(true)
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    globalProperties.set(mapOf("apis" to "", "models" to ""))
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "skipDefaultInterface" to "true",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "useBeanValidation" to "true",
            "generateJsonSetterNullsAnnotations" to "true",
            "optionalNonNullPropertyJsonSetterNulls" to "FAIL",
            "openApiNullable" to "false",
            "useTags" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "exceptionHandler" to "false",
            "gradleBuildFile" to "false",
            "useSwaggerUI" to "false",
            "dateLibrary" to "java8",
            "sourceFolder" to "src/main/kotlin",
            "enumPropertyNaming" to "UPPERCASE",
            "companionObject" to "false",
        ),
    )
    inputs.files(schemaFiles).withPropertyName("schemaTree").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(
        layout.projectDirectory.dir("templates"),
    ).withPropertyName("templates").withPathSensitivity(PathSensitivity.RELATIVE)
}

kotlin {
    jvmToolchain(21)
    sourceSets.named("main") {
        kotlin.srcDir(generatedDirectory.map { it.dir("src/main/kotlin") })
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateContract)
}

tasks.named("check") {
    dependsOn(tasks.named("openApiValidate"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn(bundleContract)
    val installation = gradle.gradleHomeDir
    if (installation == null) {
        throw GradleException("Gradle installation directory is unavailable")
    }
    systemProperty("aigram.test.gradleHome", installation.absolutePath)
}

ktlint {
    version.set("1.7.1")
    filter {
        // Generated roots have paths relative to their source directory, not to the project.
        exclude { it.file.toPath().startsWith(generatedDirectory.get().asFile.toPath()) }
    }
}

/** Keep named model references intact so recursive schemas remain finite and strongly typed. */
fun bundleSchema(source: Path): Map<*, *> {
    val rootFile = source.toAbsolutePath().normalize()
    val schemaRoot = rootFile.parent
    val yaml = Yaml(SafeConstructor(LoaderOptions().apply { setAllowDuplicateKeys(false) }))
    val documents = mutableMapOf<Path, Map<*, *>>()
    fun document(path: Path): Map<*, *> = documents.getOrPut(path) {
        val parsed = Files.newBufferedReader(path).use { yaml.load<Map<*, *>>(it) }
        if (parsed == null) {
            throw GradleException("Empty schema document: $path")
        }
        parsed
    }
    fun reference(base: Path, value: String): Pair<Path, String> {
        val location = value.substringBefore('#')
        if (URI.create(location).isAbsolute) {
            throw GradleException("Contract references must be local: $value")
        }
        val file = if (location.isEmpty()) {
            base
        } else {
            base.parent.resolve(location).normalize()
        }
        if (!file.startsWith(schemaRoot) || !Files.isRegularFile(file)) {
            throw GradleException("Contract reference escapes the schema tree or is missing: $value")
        }
        return file to value.substringAfter('#', "")
    }
    fun resolve(key: Pair<Path, String>): Any {
        if (key.second.isNotEmpty() && !key.second.startsWith('/')) {
            throw GradleException("Unsupported contract reference fragment: ${key.second}")
        }
        return key.second.split('/').drop(1).stream().reduce(
            document(key.first) as Any,
            { node, part ->
                val resolved = (node as? Map<*, *>)?.get(part.replace("~1", "/").replace("~0", "~"))
                if (resolved == null) {
                    throw GradleException("Contract reference does not exist: ${key.first}#${key.second}")
                }
                resolved
            },
            { _, right -> right },
        )
    }
    val original = document(rootFile)
    val components = original["components"] as? Map<*, *>
    if (components == null) {
        throw GradleException("Contract components are missing")
    }
    val schemas = components["schemas"] as? Map<*, *>
    if (schemas == null) {
        throw GradleException("Contract component schemas are missing")
    }
    val named = mutableMapOf<Pair<Path, String>, String>()
    schemas.entries.stream().forEach { entry ->
        val name = entry.key
        if (name !is String) {
            throw GradleException("Contract schema names must be strings")
        }
        named[rootFile to "/components/schemas/${name.replace("~", "~0").replace("/", "~1")}"] = name
        val external = (entry.value as? Map<*, *>)?.get("\$ref") as? String
        if (external != null) {
            named[reference(rootFile, external)] = name
        }
    }
    fun copy(node: Any?, base: Path, expanding: Set<Pair<Path, String>> = emptySet()): Any? = when (node) {
        is Map<*, *> -> {
            val ref = node["\$ref"] as? String
            val key = ref?.let { reference(base, it) }
            val model = if (key == null) {
                null
            } else {
                named[key]
            }
            if (key != null && model == null) {
                if (key in expanding) {
                    throw GradleException("Unnamed recursive contract reference: ${key.first}#${key.second}")
                }
                copy(resolve(key), key.first, expanding + key)
            } else {
                val mapped = linkedMapOf<Any?, Any?>()
                node.entries.stream().forEach { entry ->
                    mapped[entry.key] = if (entry.key == "\$ref" && model != null) {
                        "#/components/schemas/$model"
                    } else {
                        copy(entry.value, base, expanding)
                    }
                }
                mapped
            }
        }
        is List<*> -> node.stream().map { copy(it, base, expanding) }.toList()
        else -> node
    }
    val definitions = linkedMapOf<Any?, Any?>()
    schemas.entries.stream().forEach { entry ->
        val ref = (entry.value as? Map<*, *>)?.get("\$ref") as? String
        val key = ref?.let { reference(rootFile, it) }
        definitions[entry.key] = if (key == null) {
            copy(entry.value, rootFile)
        } else {
            copy(resolve(key), key.first)
        }
    }
    val bundled = (copy(original, rootFile) as Map<*, *>).toMutableMap()
    val bundledComponents = (bundled["components"] as Map<*, *>).toMutableMap()
    bundledComponents["schemas"] = definitions
    bundled["components"] = bundledComponents
    return bundled
}
