plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.purride.pixeldesign"
    compileSdk {
        version = release(36)
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
    // 产品视觉模型直接向消费者暴露 PixelColor，使用 api 保持类型边界完整。
    api(project(":pixel-engine"))
    testImplementation(libs.junit)
}
