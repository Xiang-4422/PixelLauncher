plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.purride.pixellauncherv2"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixellauncherv2"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

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
    lint {
        // Adopt lint as a CI gate on an existing codebase: grandfather the
        // pre-existing findings via a baseline so the gate catches *new*
        // regressions. The baselined debt is tracked separately (L-A5).
        baseline = file("lint-baseline.xml")
    }
    buildToolsVersion = "36.0.0"
}

dependencies {
    implementation(project(":pixel-engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.jbox2d)
    implementation(libs.pinyin4j)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
