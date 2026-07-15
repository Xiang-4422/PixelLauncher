package com.purride.pixelcompose

import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证可选 Compose artifact 已公开真正的 Host wrapper，而不是空边界锚点。 */
class PixelComposeApiPresenceTest {
    /** 顶层 API class 必须包含编译后的 PixelHost Composable 入口。 */
    @Test
    fun exposesPixelHostComposableEntryPoint() {
        /** Kotlin 顶层 PixelComposeHost 文件生成的 JVM class。 */
        val apiClass = Class.forName("com.purride.pixelcompose.PixelComposeHostKt")
        /** 当前 class 对外暴露的 JVM 方法名集合。 */
        val methodNames = apiClass.declaredMethods.map { method -> method.name }.toSet()

        assertTrue("PixelHost" in methodNames)
    }
}
