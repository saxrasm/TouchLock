plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "com.touchlock.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.touchlock.app"
        minSdk        = 26
        targetSdk     = 34
        versionCode   = 1
        versionName   = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ── Signing ───────────────────────────────────────────────────────────────
    // To build a signed release:
    //   1.  Run: keytool -genkey -v -keystore touchlock.jks -alias touchlock
    //            -keyalg RSA -keysize 2048 -validity 10000
    //   2.  Place touchlock.jks in the project ROOT (never commit to git).
    //   3.  Set environment variables:
    //         TOUCHLOCK_STORE_PASSWORD   (keystore password)
    //         TOUCHLOCK_KEY_ALIAS        (alias, e.g. "touchlock")
    //         TOUCHLOCK_KEY_PASSWORD     (key password)
    //   4.  Run: ./gradlew bundleRelease  or  ./gradlew assembleRelease
    //
    // The signing block below reads from env vars so credentials are never
    // hardcoded in source control.
    signingConfigs {
        create("release") {
            storeFile     = rootProject.file("touchlock.jks")
            storePassword = System.getenv("TOUCHLOCK_STORE_PASSWORD") ?: ""
            keyAlias      = System.getenv("TOUCHLOCK_KEY_ALIAS")      ?: "touchlock"
            keyPassword   = System.getenv("TOUCHLOCK_KEY_PASSWORD")   ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true            // enable R8 shrinking + obfuscation
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)                 // viewModels() delegate
    implementation(libs.androidx.lifecycle.runtime.ktx)        // repeatOnLifecycle()
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.cardview)
}
