plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.github.triplet.play")
}

// Release signing is injected by CI (see .github/workflows/release.yml); local builds stay debug-only.
val releaseKeystorePath: String? = System.getenv("TANDEMMOTO_KEYSTORE_PATH")

android {
    namespace = "com.tandemmoto"
    // TODO(Phase 0): finalize compileSdk/minSdk against a modern Android baseline,
    // accounting for background execution / foreground service restrictions (PRD §4 Platform).
    // 37 is required by navigation 2.10 / lifecycle 2.11; targetSdk stays 36 until tested separately.
    compileSdk = 37

    defaultConfig {
        // Overridable so contributors can publish a fork to their own Play account.
        applicationId = providers.gradleProperty("tandemmoto.applicationId")
            .getOrElse("com.tandemmoto")
        minSdk = 26
        targetSdk = 36
        // CI derives these from the git tag (vX.Y.Z) and run number on release.
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-dev"
        resValue("string", "app_name", "TandemMoto")
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
        // PR test builds for the separate "TandemMoto QA" Play app (docs/TESTING_ON_PLAY.md).
        // Left unsigned: .github/workflows/play-test.yml signs it with the QA upload key.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            signingConfig = null
            matchingFallbacks += "release"
            resValue("string", "app_name", "TandemMoto QA")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // AGP 9 disables resValue generation by default; app_name is set per build type with it.
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

// Google Play publishing (Gradle Play Publisher). CI supplies the service-account key;
// see docs/RELEASING.md (production app) and docs/TESTING_ON_PLAY.md (QA app).
play {
    serviceAccountCredentials.set(
        file(System.getenv("PLAY_SERVICE_ACCOUNT_JSON_PATH") ?: "play-service-account.json")
    )
    defaultToAppBundles.set(true)
    track.set("internal")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // TODO(Phase 0-2): add and pin —
    //   androidx.media3 (exoplayer + session), androidx.room,
    //   Wi-Fi P2P uses the platform android.net.wifi.p2p APIs directly (no extra dep),
    //   Opus + WebRTC AudioProcessing via native/AAR deps (see docs/DEVELOPMENT_PLAN.md §2).

    testImplementation("junit:junit:4.13.2")
    // Compose UI tests run on the JVM via Robolectric, so CI's testDebugUnitTest covers them.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.17")
}
