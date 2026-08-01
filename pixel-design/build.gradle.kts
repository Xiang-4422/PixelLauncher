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

    androidResources {
        // 共享 indexed glyph loader 通过 AssetFileDescriptor mmap 未压缩二进制。
        noCompress += "bin"
    }
}

dependencies {
    // 产品视觉模型直接向消费者暴露 PixelColor，使用 api 保持类型边界完整。
    api(project(":pixel-engine"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

/** 从唯一 JSON 目录更新共享产品字体 Kotlin 目录。 */
tasks.register<Exec>("generateProductFontCatalog") {
    group = "build"
    description = "Generates the shared product font catalog from fonts/font_catalog.json."
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("tools/generate_pixel_font_catalog.py"))
}

/** 验证已提交的共享字体目录与唯一 JSON 声明完全一致。 */
val checkProductFontCatalog by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks that GeneratedProductFontCatalog.kt is current."
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("tools/generate_pixel_font_catalog.py"), "--check")
}

/** 校验所有共享字体 pack 均可达且与 catalog 摘要锁一致。 */
val checkProductFontAssets by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks shared glyph-pack manifests, reachability and hashes."
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("tools/check_pixel_font_assets.py"))
}

/** 共享设计模块的标准检查必须阻止字体声明、资源与生成代码漂移。 */
tasks.named("check") {
    dependsOn(checkProductFontCatalog)
    dependsOn(checkProductFontAssets)
}
