import org.sonarqube.gradle.SonarExtension

// Root build file — only declares plugins used across sub-projects.
// Actual configuration lives in each sub-project's own build.gradle.kts.

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
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
}

dependencyLocking {
    lockAllConfigurations()
    lockFile.set(layout.projectDirectory.file("gradle.lockfile"))
}

apply(plugin = "org.sonarqube")

configure<SonarExtension> {
    properties {
        property("sonar.projectKey", "AAUSoftwareEngineering2_SE2_Gruppenprojekt")
        property("sonar.organization", "aausoftwareengineering2")
        property("sonar.host.url", "https://sonarcloud.io")

        property(
            "sonar.exclusions",
            listOf(
                "**/ui/**",
                "**/audio/**",
                ".github/**",
                "deploy/**",
                ".dockerignore",
            ).joinToString(","),
        )
        property("sonar.coverage.exclusions", "**/ui/**,**/audio/**")
        property("sonar.kotlin.source.version", "2.0")
    }
}

tasks.named("sonar") {
    dependsOn(
        ":shared:jacocoTestReport",
        ":server:jacocoTestReport",
        ":app:jacocoTestReport",
    )
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "jacoco")

    // Configure Sonar properties for each subproject
    apply(plugin = "org.sonarqube")

    dependencyLocking {
        lockAllConfigurations()
        lockFile.set(rootProject.layout.projectDirectory.file("gradle.lockfile"))
    }

    // This allows each subproject to report its own coverage to Sonar
    extensions.configure<SonarExtension> {
        properties {
            val reportPath = if (project.name == "app") {
                "${project.layout.buildDirectory.get()}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
            } else {
                "${project.layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml"
            }
            property("sonar.coverage.jacoco.xmlReportPaths", reportPath)
        }
    }

    // Konfiguration für alle Jacoco-Reports (XML für Sonar)
    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
