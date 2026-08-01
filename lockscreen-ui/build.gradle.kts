plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.purride.pixellockscreen.ui"
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
    // 公共更新接口直接使用产品主题枚举，因此向消费者暴露视觉模块。
    api(project(":pixel-design"))
    // PixelHostView 和 Widget 树属于锁屏 UI 的内部实现。
    implementation(project(":pixel-engine"))
    testImplementation(libs.junit)
}
