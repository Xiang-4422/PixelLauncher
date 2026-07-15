package com.purride.pixelui.testing

import com.purride.pixelui.Text
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证独立 pixel-testing artifact 能驱动真实 runtime/widget 树。 */
class PixelTestingArtifactTest {
    /** 验证 pump、finder 和离屏渲染通过公开测试 API 协同工作。 */
    @Test
    fun pumpsAndFindsRenderedText() {
        /** 独立 testing 坐标提供的确定性离屏驱动器。 */
        val tester = PixelTester()

        tester.pumpWidget(Text("TESTING"), logicalWidth = 32, logicalHeight = 8)

        assertTrue(tester.exists(find.byText("TESTING")))
        tester.dispose()
    }
}
