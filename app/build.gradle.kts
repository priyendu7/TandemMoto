plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

// Release signing is injected by CI (see .github/workflows/release.yml); local builds stay debug-only.
val releaseKeystorePath: String? = System.getenv("TANDEMMOTO_KEYSTORE_PATH")

android {
    namespace = "com.tandemmoto"
    // TODO(Phase 0): finalize compileSdk/minSdk against a modern Android baseline,
    // accounting for background execution / foreground service restrictions (PRD §4 Platform).
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tandemmoto"
        minSdk = 26
        targetSdk = 35
        // CI derives these from the git tag (vX.Y.Z) and run number on release.
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-dev"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("TANDEMMOTO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TANDEMMOTO_KEY_ALIAS")
                keyPassword = System.getenv("TANDEMMOTO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    // Minimal Compose shell so the app builds and launches.
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")

    // TODO(Phase 0-2): add and pin —
    //   androidx.media3 (exoplayer + session), androidx.room,
    //   Wi-Fi P2P uses the platform android.net.wifi.p2p APIs directly (no extra dep),
    //   Opus + WebRTC AudioProcessing via native/AAR deps (see docs/DEVELOPMENT_PLAN.md §2).

    testImplementation("junit:junit:4.13.2")
}
