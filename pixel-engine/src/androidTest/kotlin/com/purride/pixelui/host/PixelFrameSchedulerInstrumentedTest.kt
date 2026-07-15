package com.purride.pixelui.host

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 验证 runtime 默认调度契约能在 Android artifact 中解析并使用 Choreographer。 */
@RunWith(AndroidJUnit4::class)
class PixelFrameSchedulerInstrumentedTest {
    /**
     * `Default` 应稳定返回 Android 可取消调度器单例，而不是在兼容解析处发生类加载失败。
     */
    @Test
    fun defaultResolvesAndroidCancellableSchedulerSingleton() {
        /** 第一次解析出的 Android 默认调度器。 */
        val firstScheduler: PixelFrameScheduler = PixelFrameScheduler.Default
        /** 第二次解析用于确认 Kotlin object 单例身份没有因反射桥改变。 */
        val secondScheduler: PixelFrameScheduler = PixelFrameScheduler.Default

        assertTrue(firstScheduler is PixelCancellableFrameScheduler)
        assertSame(firstScheduler, secondScheduler)
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
}
