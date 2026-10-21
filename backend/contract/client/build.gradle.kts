plugins {
    kotlin("jvm")
    `java-library`
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":backend:contract:client:openapi"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.named("check") {
    dependsOn(":backend:contract:client:openapi:check")
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    version.set("1.7.1")
    filter {
        exclude("**/build/**")
    }
}
