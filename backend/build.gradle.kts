plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
}

group = "ru.boltunya"
version = "2.5.1"

repositories {
    mavenCentral()
    maven("https://mvn.mchv.eu/repository/mchv/")
}

dependencies {
    implementation("com.google.inject:guice:7.0.0")
    implementation("com.github.oshi:oshi-core:7.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("io.modelcontextprotocol.sdk:mcp:2.1.0")
    implementation(platform("it.tdlight:tdlight-java-bom:3.5.7+td.1.8.68"))
    implementation("it.tdlight:tdlight-java")
    runtimeOnly("it.tdlight:tdlight-natives:4.0.594:windows_amd64")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    implementation("org.hibernate.validator:hibernate-validator:8.0.3.Final")
    implementation("org.glassfish:jakarta.el:4.0.2")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("ru.boltunya.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
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
