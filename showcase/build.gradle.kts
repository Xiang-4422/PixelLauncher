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
    // 常规画廊直接展示引擎能力，锁屏入口则复用产品级静态宿主。
    implementation(project(":pixel-engine"))
    implementation(project(":lockscreen-ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
}
