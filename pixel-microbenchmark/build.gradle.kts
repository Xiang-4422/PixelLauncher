plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.androidx.benchmark)
}

android {
    namespace = "com.purride.pixelmicrobenchmark"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
        // 专用 runner 保留 AndroidX 隔离 Activity，并在 MIUI 上解除测试包的后台 Activity 限制。
        testInstrumentationRunner = "com.purride.pixelmicrobenchmark.PixelAndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildToolsVersion = "36.0.0"
}

dependencies {
    implementation(project(":pixel-engine"))
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
