// Top-level build.gradle.kts
plugins {
    // Diese kommen aus deinem Version Catalog (libs.versions.toml)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Diese laden wir direkt, um Versions-Konflikte zu vermeiden
    id("org.sonarqube") version "6.0.1.5171"
    id("com.diffplug.spotless") version "7.0.2"
}

// SonarCloud Konfiguration direkt im Root
sonar {
    properties {
        property("sonar.projectKey", "londera_SE2_Gruppenprojekt")
        property("sonar.organization", "londera")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

// Google Coding Standards (Spotless) für alle Unterprojekte (wie 'app')
subprojects {
    // Das Plugin wird erst geladen, wenn das Subprojekt (app) dran ist
    plugins.withId("com.diffplug.spotless") {
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                target("**/*.kt")
                targetExclude("**/build/**/*.kt")
                ktlint().editorConfigOverride(mapOf(
                    "indent_size" to "4",
                    "continuation_indent_size" to "4",
                    "kotlin_imports_layout" to "google"
                ))
            }
        }
    }
}