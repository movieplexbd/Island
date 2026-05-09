// ╔══════════════════════════════════════════════════════╗
// ║      Dynamic Island V3 — app/build.gradle.kts        ║
// ╚══════════════════════════════════════════════════════╝
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "com.dynamicisland"
    compileSdk = 35

    defaultConfig {
        applicationId   = "com.dynamicisland"
        minSdk          = 26
        targetSdk       = 35
        versionCode     = 3
        versionName     = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable        = true
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose     = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
        xmlReport = true
        textReport = true
        absolutePaths = false
    }

    splits {
        abi {
            isEnable     = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    // ── Core ─────────────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // ── Jetpack Compose BOM ───────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.animation:animation-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ── Media ─────────────────────────────────────────────────────────────────
    implementation("androidx.media:media:1.7.0")

    // ── V3: DataStore (offline cache) ─────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── V3: WorkManager (background weather refresh) ──────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ── Lottie ────────────────────────────────────────────────────────────────
    implementation("com.airbnb.android:lottie-compose:6.4.1")

    // ── Window (foldable support) ─────────────────────────────────────────────
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.window:window-java:1.3.0")

    // ── Palette (wallpaper color extraction) ──────────────────────────────────
    implementation("androidx.palette:palette-ktx:1.0.0")

    // ── Coil (async image / album art) ────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ── JSON (offline cache serialization) ────────────────────────────────────
    implementation("org.json:json:20240303")

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
