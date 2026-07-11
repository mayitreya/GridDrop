plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.griddrop"
    compileSdk = 36          // Android 16 — needed for the config-based LocalOnlyHotspot start.

    defaultConfig {
        applicationId = "com.griddrop"
        minSdk = 26          // LocalOnlyHotspot was introduced in API 26 (Android 8.0).
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Shrink code + resources for a much smaller, faster-installing APK (helps old devices
            // like a Galaxy J7). Signed with the debug key so it still sideloads with no keystore.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    // --- Jetpack Compose UI ---
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    // --- Embedded HTTP server (Ktor 3, CIO engine — pure Kotlin, no native bits) ---
    implementation("io.ktor:ktor-server-core:3.0.2")
    implementation("io.ktor:ktor-server-cio:3.0.2")
    implementation("io.ktor:ktor-server-cors:3.0.2")
    implementation("io.ktor:ktor-server-status-pages:3.0.2")

    // --- QR generation ---
    implementation("com.google.zxing:core:3.5.3")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
