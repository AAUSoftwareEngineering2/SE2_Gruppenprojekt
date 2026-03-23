plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("jacoco")
}

android {
    // KORREKTUR: Namespace muss zu deinem Code (at.aau.serg...) passen!
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
    // ... restliche android-Konfiguration (compileOptions, buildFeatures) bleibt gleich ...
}

dependencies {
    // TEST-DEPENDENCIES:
	implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    testImplementation(libs.junit) 
    // Mockito!
    testImplementation("org.mockito:mockito-core:5.11.0") 
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
	
}