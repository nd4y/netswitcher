plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "icu.nd4y.netswitcher"
    // 36 (Android 16) is what Live Update notifications need.
    compileSdk = 36

    defaultConfig {
        applicationId = "icu.nd4y.netswitcher"
        minSdk = 31
        targetSdk = 36
        versionCode = 12
        versionName = "1.11"
    }

    // Release signing comes from the environment so the key never lives in the repo.
    // Without it the release APK stays unsigned, which is fine for a local build but
    // useless for distribution: Obtainium needs a stable signature to update in place.
    val keystorePath = System.getenv("NETSWITCHER_KEYSTORE")
    if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("NETSWITCHER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NETSWITCHER_KEY_ALIAS") ?: "netswitcher"
                keyPassword = System.getenv("NETSWITCHER_KEY_PASSWORD")
                // v3 supersedes v2 and leaves the door open to rotating the key
                // later. It landed in Android 9, far below this app's minSdk 31.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // Reflection into hidden framework APIs + Shizuku makes shrinking risky
            // for very little gain on an app this small.
            isMinifyEnabled = false
            isShrinkResources = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // 1.17 is the first release with NotificationCompat.ProgressStyle (Live Updates).
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Persisted configuration
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.yaml:snakeyaml:2.2")

    // Wi-Fi QR code generation (pure-Java, no Android integration layer needed).
    implementation("com.google.zxing:core:3.5.3")

    // Home screen widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Privileged shell without root
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
