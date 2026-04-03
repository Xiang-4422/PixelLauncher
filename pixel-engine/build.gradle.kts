plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.purride.pixelengine"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":pixel-core"))
    api(project(":pixel-ui"))
    testImplementation(libs.junit)
}
