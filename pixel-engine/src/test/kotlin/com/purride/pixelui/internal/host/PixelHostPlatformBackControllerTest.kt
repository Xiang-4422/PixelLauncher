package com.purride.pixelui.internal.host

import com.purride.pixelui.PixelPredictiveBackEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** 平台 callback 注册生命周期的普通 JVM 测试；不作为 Android 设备事件验收证据。 */
class PixelHostPlatformBackControllerTest {
    /** API 24/33/34 的能力边界稳定映射为手动、完成和完整动画三档。 */
    @Test
    fun apiLevelsSelectManualInvokedAndAnimationCapabilities() {
        assertEquals(
            PixelHostAndroidBackCapability.Manual,
            resolvePixelHostAndroidBackCapability(24),
        )
        assertEquals(
            PixelHostAndroidBackCapability.Manual,
            resolvePixelHostAndroidBackCapability(32),
        )
        assertEquals(
            PixelHostAndroidBackCapability.Invoked,
            resolvePixelHostAndroidBackCapability(33),
        )
        assertEquals(
            PixelHostAndroidBackCapability.Animation,
            resolvePixelHostAndroidBackCapability(34),
        )
        assertEquals(
            PixelHostAndroidBackCapability.Animation,
            resolvePixelHostAndroidBackCapability(37),
        )
    }

    /** attach/availability/detach 只维持一个注册，并在释放前取消会话。 */
    @Test
    fun registrationTracksAttachAndAvailabilityWithoutDuplicates() {
        var available = false
        var registrations = 0
        var disposals = 0
        var cancellations = 0
        val registrar = PixelHostPlatformBackRegistrar {
            registrations++
            PixelHostPlatformBackRegistration { disposals++ }
        }
        val callbacks = object : PixelHostPlatformBackCallbacks {
            override fun onBackStarted(event: PixelPredictiveBackEvent) = Unit

            override fun onBackProgressed(event: PixelPredictiveBackEvent) = Unit

            override fun onBackCancelled() {
                cancellations++
            }

            override fun onBackInvoked() = Unit
        }
        val controller = PixelHostPlatformBackController(
            registrar = registrar,
            shouldRegister = { available },
            callbacks = callbacks,
        )

        controller.attach()
        assertEquals(0, registrations)
        available = true
        controller.refresh()
        controller.refresh()
        assertEquals(1, registrations)

        available = false
        controller.refresh()
        assertEquals(1, cancellations)
        assertEquals(1, disposals)

        available = true
        controller.refresh()
        assertEquals(2, registrations)
        controller.detach()
        controller.detach()
        assertEquals(2, cancellations)
        assertEquals(2, disposals)
    }

    /** 不支持该 API 的 registrar 返回 null 时不会伪造注册或取消事件。 */
    @Test
    fun unsupportedRegistrarKeepsCompatibilityPathUnregistered() {
        var attempts = 0
        val controller = PixelHostPlatformBackController(
            registrar = PixelHostPlatformBackRegistrar {
                attempts++
                null
            },
            shouldRegister = { true },
            callbacks = NoOpCallbacks,
        )

        controller.attach()
        controller.refresh()
        controller.detach()

        assertEquals(2, attempts)
    }

    /** 测试中不记录事件的 callback。 */
    private object NoOpCallbacks : PixelHostPlatformBackCallbacks {
        override fun onBackStarted(event: PixelPredictiveBackEvent) = Unit

        override fun onBackProgressed(event: PixelPredictiveBackEvent) = Unit

        override fun onBackCancelled() = Unit

        override fun onBackInvoked() = Unit
    }
}
