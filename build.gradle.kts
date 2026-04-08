import org.sonarqube.gradle.SonarExtension

// Root build file — only declares plugins used across sub-projects.
// Actual configuration lives in each sub-project's own build.gradle.kts.

// Using buildscript because the standard "alias" version
// of the Sonar plugin is currently broken in Gradle 9.
buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath(libs.sonarqube.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
}

// See the comment above "buildscript"
apply(plugin = "org.sonarqube")

configure<SonarExtension> {
    properties {
        property("sonar.projectKey", "AAUSoftwareEngineering2_SE2_Gruppenprojekt")
        property("sonar.organization", "aausoftwareengineering2")
        property("sonar.host.url", "https://sonarcloud.io")

        property("sonar.sources", "app/src/main/kotlin,server/src/main/kotlin,shared/src/main/kotlin")
        property("sonar.tests", "app/src/test/kotlin,server/src/test/kotlin,shared/src/test/kotlin")
        property("sonar.java.binaries", "**/build/classes/kotlin/main,**/build/tmp/kotlin-classes/debug")
        property("sonar.kotlin.source.version", "2.0")
        property("sonar.coverage.jacoco.xmlReportPaths", "**/build/reports/jacoco/**/*.xml")
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "jacoco")

    // Konfiguration für JVM-Module (shared & server)
    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>()) // Tests müssen vor dem Report laufen

        reports {
            xml.required.set(true) // XML für Sonar
            html.required.set(true) // Optional für lokale Ansicht
        }
    }
}
