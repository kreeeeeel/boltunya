plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    application
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
}

group = "ru.aigram"
version = providers.gradleProperty("appVersion").getOrElse("2.6.0")

repositories {
    mavenCentral()
    maven("https://mvn.mchv.eu/repository/mchv/")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(kotlin("reflect"))
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    implementation("org.springframework.ai:spring-ai-ollama:2.0.1")
    implementation("org.springframework.ai:spring-ai-openai:2.0.1")
    implementation("com.github.oshi:oshi-core:7.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("org.commonmark:commonmark:0.30.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.30.0")
    implementation("io.modelcontextprotocol.sdk:mcp:2.1.0")
    implementation(platform("it.tdlight:tdlight-java-bom:3.5.7+td.1.8.68"))
    implementation("it.tdlight:tdlight-java")
    runtimeOnly("it.tdlight:tdlight-natives:4.0.594:windows_amd64")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
    archiveClassifier.set("")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("ru.aigram.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
    val liveFlags = listOf("TIHO_LIVE_AI", "TIHO_LIVE_INTERNET", "TIHO_LIVE_MEMORY")
    (liveFlags + listOf("TIHO_TEST_MODELS", "TIHO_TEST_CASES", "TIHO_TEST_OLLAMA_URL")).forEach { key ->
        inputs.property(key, providers.environmentVariable(key).orElse(""))
    }
    val liveEnabled = { liveFlags.any { providers.environmentVariable(it).getOrElse("") == "true" } }
    outputs.upToDateWhen { !liveEnabled() }
    outputs.doNotCacheIf("Live inference and network state change outside the build") { liveEnabled() }
}

tasks.processResources {
    from("../web/out") {
        into("web")
    }
}

tasks.jar {
    manifest {
        attributes["Class-Path"] = configurations.runtimeClasspath.get().files
            .sortedBy { it.name }
            .joinToString(" ") { it.name }
    }
}

tasks.register<Exec>("buildWeb") {
    workingDir("../web")
    commandLine("cmd", "/c", "npm.cmd", "run", "build:prod")
}

tasks.register("prepareDesktop") {
    dependsOn("buildWeb")
    tasks.processResources.get().mustRunAfter("buildWeb")
    dependsOn("installDist")
}

ktlint {
    version.set("1.7.1")
    filter {
        exclude("**/build/**")
        exclude("**/.tools/**")
    }
}
