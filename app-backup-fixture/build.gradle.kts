plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.purride.pixellauncherv2.backupfixture"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixellauncherv2.backupfixture"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.legacy-backup-fixture"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildToolsVersion = "36.0.0"
}
