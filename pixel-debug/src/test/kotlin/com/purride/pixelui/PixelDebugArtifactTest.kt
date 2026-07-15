package com.purride.pixelui

import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证独立 pixel-debug artifact 的 overlay 能由公开 testing artifact 驱动。 */
class PixelDebugArtifactTest {
    /** 验证无诊断数据时仍返回带稳定 key 的零尺寸调试占位。 */
    @Test
    fun buildsDeterministicEmptyDebugOverlay() {
        /** 用于挂载 debug artifact 公开组件的离屏测试驱动器。 */
        val tester = PixelTester()

        tester.pumpWidget(
            PixelDebugOverlay(stats = null, key = "debug-overlay"),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        assertTrue(tester.exists(find.byKey("debug-overlay")))
        tester.dispose()
    }
}
