plugins {
    base
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.serialization") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0" apply false
    id("org.openapi.generator") version "7.25.0" apply false
}

allprojects {
    group = "ru.aigram"
    version = providers.gradleProperty("appVersion").getOrElse("2.6.0")
}

tasks.named("check") {
    dependsOn(":backend:check")
    dependsOn(":backend:contract:api:check")
    dependsOn(":backend:contract:client:check")
    dependsOn(":backend:contract:client:typescript:check")
}
