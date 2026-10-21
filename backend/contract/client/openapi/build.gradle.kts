import org.gradle.api.tasks.PathSensitivity
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

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
    api("org.springframework:spring-webflux")
    api("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val schemaDirectory = layout.projectDirectory.dir("../../api/schema")
val schemaFiles = fileTree(schemaDirectory)
val bundledSchema = project(":backend:contract:api").layout.buildDirectory.file("bundled/api.yaml")
val generatedDirectory = layout.buildDirectory.dir("generated/openapi")

tasks.named<ValidateTask>("openApiValidate") {
    dependsOn(":backend:contract:api:bundleContract")
    inputSpec.set(bundledSchema)
    inputs.files(schemaFiles).withPropertyName("schemaTree").withPathSensitivity(PathSensitivity.RELATIVE)
}

val generateClient = tasks.named<GenerateTask>("openApiGenerate") {
    dependsOn(tasks.named("openApiValidate"))
    generatorName.set("kotlin")
    library.set("jvm-spring-webclient")
    dependsOn(":backend:contract:api:bundleContract")
    inputSpec.set(bundledSchema)
    outputDir.set(generatedDirectory)
    templateDir.set(layout.projectDirectory.dir("templates"))
    packageName.set("ru.aigram.client.generated")
    apiPackage.set("ru.aigram.client.generated.api")
    modelPackage.set("ru.aigram.client.generated.model")
    apiNameSuffix.set("ClientGenerated")
    cleanupOutput.set(true)
    validateSpec.set(true)
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    globalProperties.set(mapOf("apis" to "", "models" to "", "supportingFiles" to ""))
    configOptions.set(
        mapOf(
            // The WebClient template's supported compatibility flag uses Jackson 2 codecs on Spring 7.
            "useSpringBoot3" to "true",
            "serializationLibrary" to "jackson",
            "useJackson3" to "false",
            "dateLibrary" to "java8",
            "requestDateConverter" to "toString",
            "mapFileBinaryToByteArray" to "true",
            "sourceFolder" to "src/main/kotlin",
            "enumPropertyNaming" to "UPPERCASE",
            "companionObject" to "false",
            "omitGradleWrapper" to "true",
            "omitGradlePluginVersions" to "true",
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
    dependsOn(generateClient)
}

tasks.named("check") {
    dependsOn(tasks.named("openApiValidate"))
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    version.set("1.7.1")
    filter {
        // Generated roots have paths relative to their source directory, not to the project.
        exclude { it.file.toPath().startsWith(generatedDirectory.get().asFile.toPath()) }
    }
}
