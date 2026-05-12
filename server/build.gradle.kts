plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    application
}

group = "at.aau.kuhhandel.server"
version = "1.0.0"

val jvmToolchainVersion = providers.gradleProperty("jvmToolchainVersion").get().toInt()

kotlin {
    jvmToolchain(jvmToolchainVersion)
}

application {
    mainClass.set("at.aau.kuhhandel.server.ServerApplicationKt")
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.kotlin.reflect)
    implementation(libs.postgresql)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
