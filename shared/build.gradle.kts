plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "at.aau.kuhhandel.shared"
version = "1.0.0"

val jvmToolchainVersion = providers.gradleProperty("jvmToolchainVersion").get().toInt()

// class file versions above JVM 21. Both Kotlin and Java must target the same
// version to avoid the "Inconsistent JVM Target Compatibility" error.
kotlin {
    jvmToolchain(jvmToolchainVersion)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
