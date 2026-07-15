package com.purride.pixelui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 验证默认宿主的隐藏编辑器不会把字体测量和 TextView 绘制带入生产帧。 */
@RunWith(AndroidJUnit4::class)
public class PixelHiddenTextInputHostInstrumentedTest {
    /** 固定 1×1 且禁止绘制，同时保留后续焦点和 InputConnection 所需的可见 View 身份。 */
    @Test
    public fun hiddenEditorUsesFixedOnePixelNoDrawSurface() {
        /** Android 主线程上创建 View 所需的应用 Context。 */
        val context: Context = ApplicationProvider.getApplicationContext()
        /** 从主线程返回的默认宿主装配。 */
        lateinit var setup: PixelHostSetup
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setup = createPixelHostSetup(context = context)
        }
        /** 隐藏编辑器在根 FrameLayout 中使用的确定性布局参数。 */
        val layoutParams = setup.textInputBridge.inputView.layoutParams as FrameLayout.LayoutParams
        assertEquals(1, layoutParams.width)
        assertEquals(1, layoutParams.height)
        assertEquals(0f, setup.textInputBridge.inputView.alpha)
        assertTrue(setup.textInputBridge.inputView.willNotDraw())
        assertNull(setup.textInputBridge.inputView.background)
        assertFalse(setup.textInputBridge.inputView.isCursorVisible)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            /** 故意给出远大于隐藏几何的测量约束，验证文本和字体不会扩大平台编辑器。 */
            val oversizedMeasureSpec = View.MeasureSpec.makeMeasureSpec(1_024, View.MeasureSpec.AT_MOST)
            setup.textInputBridge.inputView.measure(oversizedMeasureSpec, oversizedMeasureSpec)
        }
        assertEquals(1, setup.textInputBridge.inputView.measuredWidth)
        assertEquals(1, setup.textInputBridge.inputView.measuredHeight)
    }
}
