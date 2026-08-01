plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.purride.pixellockscreen"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixellockscreen"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 锁屏宿主只复用像素引擎；SystemUI 适配和产品视觉层将在独立里程碑中接入。
    implementation(project(":pixel-engine")) {
        // 注入宿主不需要应用启动基线安装器，避免传递依赖向惰性 APK 合并运行组件。
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
    }
    testImplementation(libs.junit)
}
