plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("jacoco")
}

android {
    // Namespace muss exakt zu eurer Paketstruktur passen
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    testOptions {
        unitTests {
            all {
                it.useJUnitPlatform()
            }
        }
    }
}

dependencies {
    implementation(libs.krossbow.websocket.okhttp)
    implementation(libs.krossbow.stomp.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    
    // Test-Abhängigkeiten
    testImplementation(libs.junit)
    // Mockito ist zwingend erforderlich für eure BasicCoverageUnitTests
    testImplementation("org.mockito:mockito-core:5.11.0")
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
}