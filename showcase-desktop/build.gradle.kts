plugins {
    // AGP 9 的内置 Kotlin 已把 KGP 放上构建 classpath，这里不能再声明版本。
    id("org.jetbrains.kotlin.jvm")
    application
}

/*
 * 桌面宿主：证明 pixel-engine 的核心（PixelBuffer / 字体 / 场景数学）
 * 是纯 Kotlin——同一批场景源码不改一行跑在 Mac 窗口里。
 *
 * 依赖引擎 AAR 的 classes.jar（只触碰 pixelcore，不加载任何 Android 类），
 * 场景源码直接从 showcase 模块共享。
 */

val engineClassesJar = rootProject.layout.projectDirectory.file(
    "pixel-engine/build/intermediates/aar_main_jar/debug/syncDebugLibJars/classes.jar",
)

dependencies {
    implementation(files(engineClassesJar))
}

sourceSets {
    main {
        kotlin {
            srcDir("../showcase/src/main/kotlin")
            include(
                "com/purride/pixelshowcase/desktop/**",
                "com/purride/pixelshowcase/scenes/**",
                "com/purride/pixelshowcase/DemoScene.kt",
            )
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(":pixel-engine:assembleDebug")
}

application {
    mainClass.set("com.purride.pixelshowcase.desktop.DesktopShowcaseKt")
}
