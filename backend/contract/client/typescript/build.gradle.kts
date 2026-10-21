import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

plugins {
    base
    id("org.openapi.generator")
}

group = "ru.aigram"
version = rootProject.version

val schemaDirectory = layout.projectDirectory.dir("../../api/schema")
val contract = project(":backend:contract:api").layout.buildDirectory.file("bundled/api.yaml")
val generatedClient = layout.buildDirectory.dir("generated/openapi")
val npmCommand = if (System.getProperty("os.name").startsWith("Windows")) {
    listOf("cmd", "/c", "npm.cmd")
} else {
    listOf("npm")
}

val validateContract by tasks.registering(ValidateTask::class) {
    dependsOn(":backend:contract:api:bundleContract")
    inputSpec.set(contract)
    inputs.files(fileTree(schemaDirectory)).withPathSensitivity(PathSensitivity.RELATIVE)
}

val generateClient by tasks.registering(GenerateTask::class) {
    dependsOn(validateContract, ":backend:contract:api:bundleContract")
    generatorName.set("typescript-fetch")
    inputSpec.set(contract)
    outputDir.set(generatedClient)
    templateDir.set(layout.projectDirectory.dir("templates"))
    modelNameMappings.set(mapOf("Error" to "ApiError"))
    cleanupOutput.set(true)
    configOptions.set(
        mapOf(
            "npmName" to "@aigram/api-client",
            "npmVersion" to project.version.toString(),
            "supportsES6" to "true",
            "importFileExtension" to ".js",
            "modelPropertyNaming" to "original",
            "paramNaming" to "camelCase",
            "enumPropertyNaming" to "original",
            "dateLibrary" to "string",
            "withoutRuntimeChecks" to "true",
            "useSingleRequestParameter" to "true",
            "prefixParameterInterfaces" to "true",
            "hideGenerationTimestamp" to "true",
        ),
    )
    globalProperties.set(
        mapOf(
            "apis" to "",
            "models" to "",
            "supportingFiles" to "",
            "apiDocs" to "false",
            "modelDocs" to "false",
            "apiTests" to "false",
            "modelTests" to "false",
        ),
    )
    inputs.files(fileTree(schemaDirectory)).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("templates"))
    outputs.dir(generatedClient)
}

val npmCi by tasks.registering(Exec::class) {
    dependsOn(generateClient)
    workingDir(layout.projectDirectory)
    commandLine(npmCommand + listOf("ci", "--ignore-scripts", "--no-audit", "--no-fund"))
    inputs.files("package.json", "package-lock.json")
    outputs.dir("node_modules")
}

val typecheckClient by tasks.registering(Exec::class) {
    dependsOn(npmCi)
    workingDir(layout.projectDirectory)
    commandLine(npmCommand + listOf("run", "typecheck"))
    inputs.dir(generatedClient)
    inputs.files("package.json", "package-lock.json", "tsconfig.json")
}

val compileClient by tasks.registering(Exec::class) {
    dependsOn(typecheckClient)
    workingDir(layout.projectDirectory)
    commandLine(npmCommand + listOf("run", "build"))
    environment("AIGRAM_CLIENT_VERSION", project.version.toString())
    inputs.property("clientVersion", project.version.toString())
    inputs.dir(generatedClient)
    inputs.dir("scripts")
    inputs.files("package.json", "package-lock.json", "tsconfig.json")
    inputs.files("templates/NOTICE.md", "templates/LICENSE.txt")
    outputs.dir(layout.buildDirectory.dir("dist"))
}

val packageClient by tasks.registering(Exec::class) {
    dependsOn(compileClient)
    workingDir(layout.projectDirectory)
    commandLine(npmCommand + listOf("run", "pack"))
    inputs.dir(layout.buildDirectory.dir("dist"))
    inputs.dir("scripts")
    inputs.files("package.json", "package-lock.json")
    outputs.dir(layout.buildDirectory.dir("package"))
}

val consumerSmoke by tasks.registering(Exec::class) {
    dependsOn(packageClient)
    workingDir(layout.projectDirectory)
    commandLine(npmCommand + listOf("run", "test:consumer"))
    inputs.dir("tests")
    inputs.files("package.json", "package-lock.json")
    inputs.dir(layout.buildDirectory.dir("package"))
}

tasks.assemble {
    dependsOn(packageClient)
}

tasks.check {
    dependsOn(consumerSmoke)
}
