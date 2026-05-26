plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.purride.pixelminimal"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixelminimal"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildToolsVersion = "36.0.0"
}

dependencies {
    implementation(project(":pixel-engine"))
}
