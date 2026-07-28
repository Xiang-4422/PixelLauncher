import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 可选正式签名配置文件；缺失时 Release 保持未签名，Debug 与 CI 不受影响。
val keystorePropertiesFile = rootProject.file("keystore.properties")
// 签名属性只从被 Git 忽略的本地文件加载，不提供内置凭据。
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
// 只有提供 storeFile 时才创建 Release signingConfig。
val hasReleaseKeystore = keystoreProperties.containsKey("storeFile")

/** 允许备份迁移验收构建高于历史夹具的版本号；普通构建仍保持版本 1。 */
val pixelAppVersionCode = providers.gradleProperty("pixelAppVersionCode")
    .map { value -> value.toInt() }
    .getOrElse(1)

/** 允许备份迁移验收使用独立包名，避免清除日常 debug 应用数据。 */
val pixelAppDebugApplicationIdSuffix = providers.gradleProperty("pixelAppDebugApplicationIdSuffix")
    .getOrElse(".debug")

require(pixelAppVersionCode > 0) { "pixelAppVersionCode must be positive." }
require(pixelAppDebugApplicationIdSuffix.startsWith(".")) {
    "pixelAppDebugApplicationIdSuffix must start with a dot."
}

android {
    namespace = "com.purride.pixellauncherv2"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixellauncherv2"
        minSdk = 24
        targetSdk = 36
        versionCode = pixelAppVersionCode
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = pixelAppDebugApplicationIdSuffix
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = false
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        // Adopt lint as a CI gate on an existing codebase: grandfather the
        // pre-existing findings via a baseline so the gate catches *new*
        // regressions. The baselined debt is tracked separately (L-A5).
        baseline = file("lint-baseline.xml")
    }
    buildToolsVersion = "36.0.0"
}

dependencies {
    // Launcher 直接消费唯一的 Pixel Engine SDK 模块。
    implementation(project(":pixel-engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.pinyin4j)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.mediapipe.tasks.vision)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

/** 从唯一 JSON 目录更新 Launcher 的只读 Kotlin 字体目录。 */
tasks.register<Exec>("generatePixelFontCatalog") {
    group = "build"
    description = "Generates the Launcher font catalog from fonts/font_catalog.json."
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("tools/generate_pixel_font_catalog.py"))
}

/** 验证已提交的 Kotlin 字体目录与唯一 JSON 声明完全一致。 */
val checkPixelFontCatalog by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks that GeneratedPixelFontCatalog.kt is current."
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("tools/generate_pixel_font_catalog.py"), "--check")
}

/** App 的标准检查必须阻止字体声明与生成代码漂移。 */
tasks.named("check") {
    dependsOn(checkPixelFontCatalog)
}
