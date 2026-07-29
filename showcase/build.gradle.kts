plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.purride.pixelshowcase"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixelshowcase"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 展示应用只依赖引擎本体：它存在的意义就是证明引擎可以独立使用。
    implementation(project(":pixel-engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
