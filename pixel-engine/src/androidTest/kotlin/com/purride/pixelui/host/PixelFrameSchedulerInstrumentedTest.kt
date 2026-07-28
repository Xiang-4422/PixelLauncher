package com.purride.pixelui.host

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 验证 runtime 默认调度契约能在 Android artifact 中解析并使用 Choreographer。 */
@RunWith(AndroidJUnit4::class)
class PixelFrameSchedulerInstrumentedTest {
    /**
     * `Default` 应稳定返回同一个 Android 调度器单例，且其注册句柄支持真实取消。
     */
    @Test
    fun defaultResolvesAndroidSchedulerSingletonWithCancellableRegistration() {
        /** 第一次解析出的 Android 默认调度器。 */
        val firstScheduler: PixelFrameScheduler = PixelFrameScheduler.Default
        /** 第二次解析用于确认 Kotlin object 单例身份稳定。 */
        val secondScheduler: PixelFrameScheduler = PixelFrameScheduler.Default
        /** 回调是否被真正移除；取消后不应再有任何交付。 */
        val fired = AtomicBoolean(false)
        /** 主线程注册后立即取消得到的句柄状态。 */
        val cancelled = AtomicBoolean(false)

        assertSame(firstScheduler, secondScheduler)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            /** 注册后立刻取消的一次性 Choreographer 回调。 */
            val registration = firstScheduler.scheduleFrame { fired.set(true) }
            cancelled.set(registration.cancel())
            assertFalse(registration.isPending)
        }

        assertTrue("Pending Choreographer callback must be cancellable", cancelled.get())
        Thread.sleep(FRAME_SETTLE_MILLIS)
        assertFalse("Cancelled Choreographer callback must never fire", fired.get())
    }

    /**
     * 默认调度器注册的回调必须由 Android 主 Looper 在真实系统帧到来时交付。
     */
    @Test
    fun defaultDeliversFrameOnAndroidMainLooper() {
        /** instrumentation 用于在 Android 主线程安全注册 Choreographer 回调。 */
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        /** latch 把异步系统帧交付转换为有上限的测试等待。 */
        val delivered = CountDownLatch(1)
        /** callbackLooper 记录实际执行回调的 Looper。 */
        val callbackLooper = AtomicReference<Looper?>()

        instrumentation.runOnMainSync {
            PixelFrameScheduler.Default.scheduleFrame {
                callbackLooper.set(Looper.myLooper())
                delivered.countDown()
            }
        }

        assertTrue("Android Choreographer callback was not delivered", delivered.await(3, TimeUnit.SECONDS))
        assertSame(Looper.getMainLooper(), callbackLooper.get())
    }

    /** 让被取消的回调有足够时间证明它确实不会再交付。 */
    private companion object {
        /** 覆盖数帧的等待时长，足以让未取消的回调必然触发。 */
        const val FRAME_SETTLE_MILLIS: Long = 300L
    }
}
