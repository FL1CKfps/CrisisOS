import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    kotlin("plugin.serialization") version "2.2.10"
}

// Read API credentials from local.properties (preferred) or env vars (CI / Replit).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cred(key: String, fallback: String = ""): String =
    localProps.getProperty(key) ?: System.getenv(key) ?: fallback

android {
    namespace = "com.elv8.crisisos"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.elv8.crisisos"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ACLED_EMAIL", "\"${cred("ACLED_EMAIL")}\"")
        buildConfigField("String", "ACLED_KEY", "\"${cred("ACLED_KEY")}\"")
        buildConfigField(
            "String",
            "GDELT_BASE_URL",
            "\"${cred("GDELT_BASE_URL", "https://api.gdeltproject.org/api/v2/")}\""
        )
        buildConfigField(
            "String",
            "ACLED_BASE_URL",
            "\"${cred("ACLED_BASE_URL", "https://api.acleddata.com/")}\""
        )
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.core:core-ktx:1.15.0")

    // Core
    implementation(libs.androidx.core.ktx)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Activity
    implementation(libs.androidx.activity.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Accompanist (permissions)
    implementation(libs.accompanist.permissions)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    // Nearby Connections
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // Media
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("id.zelory:compressor:3.0.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // OSMDroid — offline map engine (OpenStreetMap, no API key needed)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // OSMBonusPack — additional overlays (circles, polygons, markers with labels)
    implementation("com.github.MKergall:osmbonuspack:6.9.0")

    // Preferences — required for OSMDroid config persistence
    implementation("androidx.preference:preference-ktx:1.2.1")

    // LiteRT-LM (on-device Gemma runtime)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.2")

    // Firebase (Analytics + Crashlytics-ready BOM, Auth-anonymous, Firestore for NGO directory)
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Networking — GDELT 2.0 + ACLED REST clients
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
ksp { arg("room.generateKotlin", "true") }
