package com.purride.pixelui

import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** 验证独立 pixel-android artifact 能完成真实 Android Host 装配与释放。 */
@RunWith(AndroidJUnit4::class)
class PixelAndroidArtifactInstrumentedTest {
    /** 验证默认装配只创建一个 HostView 和一个隐藏输入桥，并保持公开对象引用一致。 */
    @Test
    fun createsAndDisposesDefaultHostSetup() {
        /** 提供目标应用 Context 与主线程调度的设备 instrumentation。 */
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            /** 从独立 pixel-android 公开工厂创建的完整宿主装配。 */
            val setup = createPixelHostSetup(instrumentation.targetContext)

            assertEquals(2, setup.rootView.childCount)
            assertSame(setup.hostView, setup.rootView.getChildAt(0))
            assertSame(setup.textInputBridge.inputView, setup.rootView.getChildAt(1))
            assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, setup.hostView.layoutParams.width)
            setup.dispose()
        }
    }
}
