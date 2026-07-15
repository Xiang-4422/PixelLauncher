plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

/** 宿主包装脚本传入的目标设备硬件序列号，测试端会在任何 UI 动作前再次核对。 */
val expectedBenchmarkHardwareSerial = providers.gradleProperty("pixel.benchmark.expectedHardwareSerial")

/** 只有真实设备专用采集命令才允许把该值设为 true。 */
val allowPhysicalBenchmarkDevice = providers.gradleProperty("pixel.benchmark.allowPhysical")
    .orElse("false")

/** 可选的 instrumentation 类过滤器，长跑与普通 Macrobenchmark 必须显式隔离。 */
val benchmarkTestClass = providers.gradleProperty("pixel.benchmark.testClass")

/** 只有专用设备长跑脚本才会启用的 runner 开关。 */
val deviceSoakEnabled = providers.gradleProperty("pixel.soak.enabled").orElse("false")

/** 设备长跑请求的实际秒数；正式证据由脚本限制在 30–60 分钟。 */
val deviceSoakDurationSeconds = providers.gradleProperty("pixel.soak.durationSeconds")
    .orElse("1800")

/** 设备长跑目标进程内存采样间隔秒数。 */
val deviceSoakSampleIntervalSeconds =
    providers.gradleProperty("pixel.soak.sampleIntervalSeconds").orElse("60")

android {
    namespace = "com.purride.pixelbenchmark"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
        benchmarkTestClass.orNull?.takeIf(String::isNotBlank)?.let { testClass ->
            testInstrumentationRunnerArguments["class"] = testClass
        }
        expectedBenchmarkHardwareSerial.orNull?.let { hardwareSerial ->
            testInstrumentationRunnerArguments["pixel.benchmark.expectedHardwareSerial"] = hardwareSerial
        }
        testInstrumentationRunnerArguments["pixel.benchmark.allowPhysical"] =
            allowPhysicalBenchmarkDevice.get()
        testInstrumentationRunnerArguments["pixel.soak.enabled"] = deviceSoakEnabled.get()
        testInstrumentationRunnerArguments["pixel.soak.durationSeconds"] =
            deviceSoakDurationSeconds.get()
        testInstrumentationRunnerArguments["pixel.soak.sampleIntervalSeconds"] =
            deviceSoakSampleIntervalSeconds.get()
    }

    targetProjectPath = ":pixel-benchmark-target"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildToolsVersion = "36.0.0"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
