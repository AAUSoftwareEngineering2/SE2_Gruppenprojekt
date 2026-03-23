plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("jacoco")
}

android {
    namespace = "at.aau.serg.websocketbrokerdemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "at.aau.serg.websocketbrokerdemo"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            all {
                // KORREKTUR: Nutze den Standard JUnit 4 Runner für Android
                it.useJUnit() 
            }
        }
    }
    
	compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    // fixes JVM-Target-Error):
    kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
}

dependencies {
    // TEST-DEPENDENCIES
	
	// Mockito!
    testImplementation("org.mockito:mockito-core:5.11.0") 
	testImplementation("org.junit.vintage:junit-vintage-engine:5.10.2") // Hilft bei JUnit 4/5 Mix
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-inline:5.2.0") // Erlaubt das Mocken von finalen Kotlin-Klassen
	
	implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    testImplementation(libs.junit) 
    
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
	
	val composeVersion = "1.6.4"  // Latest stable as of 2026-03
    
    implementation("androidx.compose.ui:ui:$composeVersion")
	implementation("androidx.compose.foundation:foundation:$composeVersion")
    implementation("androidx.compose.material3:material3:1.1.2")
}