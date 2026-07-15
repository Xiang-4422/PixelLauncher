package com.purride.pixelui.internal.host

import android.view.ViewGroup
import android.window.BackEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelPredictiveBackCallback
import com.purride.pixelui.PixelPredictiveBackEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** API 34+ 真机/模拟器上的 Android `OnBackAnimationCallback` 端到端验收。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class PixelHostPredictiveBackInstrumentedTest {
    /** 使用真实 Android [BackEvent] 验证平台 adapter 的映射、取消和提交流程。 */
    @Test
    fun platformAnimationCallbackMapsCancelAndCommitIntoHostSession() {
        val events = mutableListOf<String>()
        val callback = recordingCallback(events)
        ActivityScenario.launch(PredictiveBackTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dispatcher = PixelBackDispatcher().apply {
                    registerPredictive(callback)
                }
                val host = PixelHostView(activity).apply {
                    backDispatcher = dispatcher
                }
                activity.rootView.addView(
                    host,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val host = activity.rootView.getChildAt(0) as PixelHostView
                val platformCallbacks = forwardingCallbacks(host)
                val realWindowRegistration = AndroidPixelHostBackRegistrar(host).register(NoOpCallbacks)
                assertNotNull("API 34+ attached View must expose a Window back dispatcher", realWindowRegistration)
                realWindowRegistration?.dispose()

                val platformCallback = createPixelHostOnBackAnimationCallback(platformCallbacks)
                platformCallback.onBackStarted(BackEvent(16f, 240f, 0f, BackEvent.EDGE_LEFT))
                platformCallback.onBackProgressed(BackEvent(80f, 240f, 0.65f, BackEvent.EDGE_LEFT))
                platformCallback.onBackCancelled()

                assertEquals(
                    listOf("start:Left:0.0", "progress:Left:0.65", "cancel"),
                    events,
                )

                events.clear()
                platformCallback.onBackStarted(BackEvent(1_064f, 300f, 0f, BackEvent.EDGE_RIGHT))
                platformCallback.onBackProgressed(BackEvent(840f, 300f, 0.8f, BackEvent.EDGE_RIGHT))
                platformCallback.onBackInvoked()

                assertEquals(
                    listOf("start:Right:0.0", "progress:Right:0.8", "commit"),
                    events,
                )

                events.clear()
                platformCallback.onBackInvoked()
                assertEquals(listOf("invoke"), events)
            }
        }
    }

    /** 创建在 instrumentation 主线程记录平台生命周期的预测返回 callback。 */
    private fun recordingCallback(
        events: MutableList<String>,
    ): PixelPredictiveBackCallback {
        return object : PixelPredictiveBackCallback {
            override fun onBackStarted(event: PixelPredictiveBackEvent): Boolean {
                events += "start:${event.swipeEdge}:${event.progress}"
                return true
            }

            override fun onBackProgressed(event: PixelPredictiveBackEvent) {
                events += "progress:${event.swipeEdge}:${event.progress}"
            }

            override fun onBackCancelled() {
                events += "cancel"
            }

            override fun onBackCommitted(): Boolean {
                events += "commit"
                return true
            }

            override fun onBackInvoked(): Boolean {
                events += "invoke"
                return true
            }
        }
    }

    /** 把 Android callback 逐项转发给同一 Host 的公共预测返回入口。 */
    private fun forwardingCallbacks(host: PixelHostView): PixelHostPlatformBackCallbacks {
        return object : PixelHostPlatformBackCallbacks {
            override fun onBackStarted(event: PixelPredictiveBackEvent) {
                assertTrue(host.handlePredictiveBackStarted(event))
            }

            override fun onBackProgressed(event: PixelPredictiveBackEvent) {
                host.handlePredictiveBackProgressed(event)
            }

            override fun onBackCancelled() {
                host.handlePredictiveBackCancelled()
            }

            override fun onBackInvoked() {
                assertTrue(host.handlePredictiveBackCommitted())
            }
        }
    }

    /** 只用于确认 attached View 可以在真实 Window 注册/注销 callback。 */
    private object NoOpCallbacks : PixelHostPlatformBackCallbacks {
        override fun onBackStarted(event: PixelPredictiveBackEvent) = Unit

        override fun onBackProgressed(event: PixelPredictiveBackEvent) = Unit

        override fun onBackCancelled() = Unit

        override fun onBackInvoked() = Unit
    }
}
