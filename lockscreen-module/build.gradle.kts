import java.util.Properties
import java.util.zip.ZipFile

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
        // 注入模块当前只支持已侦察的 Android 15 / API 35 SystemUI。
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        // Modern Xposed API 102 使用 Java 17 字节码；目标 SystemUI 运行在 Android 15。
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // SystemUI 运行时由 LSPosed/Vector 提供 API，不得把框架实现打入模块 APK。
    compileOnly(libs.libxposed.api)
    // 模块装配共享锁屏 UI，M4 仅做 SystemUI 签名探测，尚不挂载视图。
    implementation(project(":lockscreen-ui")) {
        // 注入宿主不需要应用启动基线安装器，避免传递依赖向模块 APK 合并运行组件。
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
    }
    testImplementation(libs.junit)
}

/** Modern Xposed APK 中必须存在且内容唯一的文本描述。 */
val xposedTextMetadata = mapOf(
    "META-INF/xposed/java_init.list" to "com.purride.pixellockscreen.PixelLockscreenModule",
    "META-INF/xposed/scope.list" to "com.android.systemui",
)

/** Debug 和 Release 两个待验证的模块 APK。 */
val xposedModuleApks = mapOf(
    "debug" to layout.buildDirectory.file("outputs/apk/debug/lockscreen-module-debug.apk"),
    "release" to layout.buildDirectory.file("outputs/apk/release/lockscreen-module-release-unsigned.apk"),
)

/**
 * 验证两个构建变体的 Modern Xposed 入口、静态作用域和 API 版本。
 *
 * 门禁同时拒绝旧式 `assets/xposed_init`，避免一个 APK 中存在两套入口语义。
 */
tasks.register("verifyXposedModulePackaging") {
    group = "verification"
    description = "Verifies the modern Xposed entry, static scope, API contract, and variants."
    dependsOn("assembleDebug", "assembleRelease")
    inputs.files(xposedModuleApks.values)
    doLast {
        xposedModuleApks.forEach { (variantName, apkProvider) ->
            /** 当前变体的最终 APK 文件。 */
            val apkFile = apkProvider.get().asFile
            check(apkFile.isFile) { "Missing $variantName Xposed module APK: $apkFile" }
            ZipFile(apkFile).use { archive ->
                xposedTextMetadata.forEach { (entryName, expectedText) ->
                    /** APK 中对应的 Modern Xposed 文本条目。 */
                    val entry = archive.getEntry(entryName)
                        ?: error("Missing $entryName in $variantName APK")
                    /** 去除行尾后的唯一声明内容。 */
                    val actualText = archive.getInputStream(entry).bufferedReader().use { reader ->
                        reader.readText().trim()
                    }
                    check(actualText == expectedText) {
                        "Unexpected $entryName in $variantName APK: $actualText"
                    }
                }
                check(archive.getEntry("assets/xposed_init") == null) {
                    "Legacy Xposed entry must not be packaged in $variantName APK"
                }
                /** API 102 和静态作用域的属性集。 */
                val moduleProperties = Properties().apply {
                    /** Modern Xposed 模块属性文件条目。 */
                    val moduleEntry = archive.getEntry("META-INF/xposed/module.prop")
                        ?: error("Missing module.prop in $variantName APK")
                    archive.getInputStream(moduleEntry).use(::load)
                }
                check(moduleProperties.getProperty("minApiVersion") == "102")
                check(moduleProperties.getProperty("targetApiVersion") == "102")
                check(moduleProperties.getProperty("staticScope") == "true")
                check(moduleProperties.getProperty("autoHotReload") == "false")
            }
        }
        listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").forEach { configurationName ->
            /** 当前变体的最终运行依赖声明。 */
            val runtimeDependencies = configurations.getByName(configurationName).allDependencies
            check(runtimeDependencies.none { dependency -> dependency.group == "io.github.libxposed" }) {
                "libxposed must remain compileOnly in $configurationName"
            }
        }
    }
}
