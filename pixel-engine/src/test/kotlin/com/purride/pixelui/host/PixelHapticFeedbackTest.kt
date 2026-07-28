package com.purride.pixelui

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.internal.HostRootWidget
import com.purride.pixelui.internal.PixelUiRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 widget 树内的震动入口只依赖 typed Host capability。 */
class PixelHapticFeedbackTest {
    /** 装配了震动 capability 时，widget 能直接触发宿主反馈。 */
    @Test
    fun performUsesHapticCapabilityFromContext() {
        /** 记录收到的语义化震动类型。 */
        val haptics = mutableListOf<PixelHapticType>()
        val runtime = PixelUiRuntime()

        runtime.render(
            root = hostRoot(
                hostServices = PixelHostCapabilitySet(
                    haptic = PixelHapticCapability { type -> haptics += type },
                ),
                child = Builder { context ->
                    assertTrue(PixelHapticFeedback.perform(context, PixelHapticType.TAP))
                    SizedBox(width = 1, height = 1)
                },
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertEquals(listOf(PixelHapticType.TAP), haptics)
        runtime.dispose()
    }

    /** 缺失震动 capability 时返回明确的不支持结果，调用方无需兜底。 */
    @Test
    fun performReturnsFalseWithoutHapticCapability() {
        val runtime = PixelUiRuntime()
        var handled = true

        runtime.render(
            root = hostRoot(
                child = Builder { context ->
                    handled = PixelHapticFeedback.perform(context, PixelHapticType.LONG_PRESS)
                    SizedBox(width = 1, height = 1)
                },
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertFalse(handled)
        runtime.dispose()
    }

    /** capability 抛错时不会穿透到 widget，调用方只看到未处理结果。 */
    @Test
    fun performReturnsFalseWhenHapticCapabilityFails() {
        val runtime = PixelUiRuntime()
        var handled = true

        runtime.render(
            root = hostRoot(
                hostServices = PixelHostCapabilitySet(
                    haptic = PixelHapticCapability { throw IllegalStateException("vibrator unavailable") },
                ),
                child = Builder { context ->
                    handled = PixelHapticFeedback.perform(context, PixelHapticType.TAP)
                    SizedBox(width = 1, height = 1)
                },
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertFalse(handled)
        runtime.dispose()
    }

    /** 构造一棵只注入指定 Host capability 的最小根树。 */
    private fun hostRoot(
        hostServices: PixelHostCapabilitySet = PixelHostCapabilitySet.Empty,
        child: Widget,
    ): Widget {
        return HostRootWidget(
            screenProfile = ScreenProfile(logicalWidth = 4, logicalHeight = 4, dotSizePx = 8),
            textRasterizer = PixelBitmapFont.Default,
            windowInsets = PixelWindowInsets.Zero,
            viewInsets = PixelWindowInsets.Zero,
            hostServices = hostServices,
            child = child,
        )
    }
}
