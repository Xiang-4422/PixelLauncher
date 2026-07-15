package com.purride.pixelengine

import com.purride.pixelcore.PixelResourceCache
import com.purride.pixelcore.PixelResourceKind
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.PixelCapabilityResult
import com.purride.pixelui.PixelCapabilityValueResult
import com.purride.pixelui.PixelHostCapabilitySet
import com.purride.pixelui.PixelNavigateBackAction
import com.purride.pixelui.PixelSavedStateCapability
import com.purride.pixelui.PixelSystemActionCapability
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.services.PixelClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 PixelEngine Builder 与实例服务隔离。 */
class PixelEngineTest {
    /** 两个 Engine 的主题、时钟、缓存、调度和 Host 服务不会隐式共享。 */
    @Test
    fun enginesKeepInjectedServicesIsolated() {
        /** 第一套完全脱离 Android 系统服务的 fake。 */
        val firstClock = FakeClock(uptimeMillis = 10L, nanoTime = 10_000L, wallTimeMillis = 100L)
        /** 第二套完全脱离 Android 系统服务的 fake。 */
        val secondClock = FakeClock(uptimeMillis = 20L, nanoTime = 20_000L, wallTimeMillis = 200L)
        /** 第一 Engine 的手动调度器。 */
        val firstScheduler = ManualFrameScheduler()
        /** 第二 Engine 的手动调度器。 */
        val secondScheduler = ManualFrameScheduler()
        /** 第一 Engine 的独立缓存。 */
        val firstCache = PixelResourceCache()
        /** 第二 Engine 的独立缓存。 */
        val secondCache = PixelResourceCache()
        /** 第一 Host 服务记录系统动作次数。 */
        val firstActionCapability = RecordingSystemActionCapability()
        /** 第二 Host 服务记录系统动作次数。 */
        val secondActionCapability = RecordingSystemActionCapability()

        /** 使用深色主题与第一套服务的 Engine。 */
        val firstEngine = PixelEngine.Builder()
            .clock(firstClock)
            .frameScheduler(firstScheduler)
            .resourceCache(firstCache)
            .resourceResolver { request ->
                PixelResourceResolution.Resolved("first/${request.key}")
            }
            .hostCapabilities(HostCapabilitiesData(density = 1f, refreshRateHz = 60f))
            .hostServices(PixelHostCapabilitySet(systemActions = firstActionCapability))
            .theme(PixelThemeTokens.Dark)
            .build()
        /** 使用亮色主题与第二套服务的 Engine。 */
        val secondEngine = PixelEngine.Builder()
            .clock(secondClock)
            .frameScheduler(secondScheduler)
            .resourceCache(secondCache)
            .resourceResolver { request ->
                PixelResourceResolution.Resolved("second/${request.key}")
            }
            .hostCapabilities(HostCapabilitiesData(density = 2f, refreshRateHz = 120f))
            .hostServices(PixelHostCapabilitySet(systemActions = secondActionCapability))
            .theme(PixelThemeTokens.Light)
            .build()

        assertNotSame(firstEngine, secondEngine)
        assertNotSame(firstEngine.services.clock, secondEngine.services.clock)
        assertNotSame(firstEngine.services.frameScheduler, secondEngine.services.frameScheduler)
        assertNotSame(firstEngine.services.resourceCache, secondEngine.services.resourceCache)
        assertSame(firstCache, firstEngine.services.resourceCache)
        assertSame(secondCache, secondEngine.services.resourceCache)
        assertEquals(PixelThemeTokens.Dark, firstEngine.theme)
        assertEquals(PixelThemeTokens.Light, secondEngine.theme)
        assertEquals(1f, firstEngine.services.hostCapabilities?.density)
        assertEquals(2f, secondEngine.services.hostCapabilities?.density)
        assertEquals(
            PixelResourceResolution.Resolved("first/icon"),
            firstEngine.services.resourceResolver.resolve(
                PixelResourceRequest("icon", PixelResourceKind.BITMAP),
            ),
        )
        assertEquals(
            PixelResourceResolution.Resolved("second/icon"),
            secondEngine.services.resourceResolver.resolve(
                PixelResourceRequest("icon", PixelResourceKind.BITMAP),
            ),
        )

        assertSame(
            PixelCapabilityResult.Handled,
            firstEngine.services.hostServices.dispatchSystemAction(PixelNavigateBackAction),
        )
        assertEquals(1, firstActionCapability.dispatchCount)
        assertEquals(0, secondActionCapability.dispatchCount)
    }

    /** Builder 默认缓存按 build 隔离，缺失可选 capability 返回明确不支持结果。 */
    @Test
    fun defaultsDoNotShareCacheAndMissingCapabilityIsExplicit() {
        /** fake 调度器避免测试读取 Android Choreographer。 */
        val scheduler = ManualFrameScheduler()
        /** 使用同一个 Builder 连续创建的第一实例。 */
        val firstEngine = PixelEngine.Builder().frameScheduler(scheduler).build()
        /** 使用同一个 Builder 连续创建的第二实例。 */
        val secondEngine = PixelEngine.Builder().frameScheduler(scheduler).build()

        assertNotSame(firstEngine.services.resourceCache, secondEngine.services.resourceCache)
        /** 空 Host 集合必须产生稳定 Unsupported，而不是静默空操作。 */
        val result = PixelHostCapabilitySet.Empty.dispatchSystemAction(PixelNavigateBackAction)
        assertTrue(result is PixelCapabilityResult.Unsupported)
        assertEquals("systemActions", (result as PixelCapabilityResult.Unsupported).capability)
        /** 支持但为空的剪贴板与缺失剪贴板 capability 必须可区分。 */
        val clipboardResult = PixelHostCapabilitySet.Empty.readClipboardText()
        assertTrue(clipboardResult is PixelCapabilityValueResult.Unsupported)
    }

    /** Host capability fallback 只补缺，并继续共享原 Engine 的有状态服务引用。 */
    @Test
    fun hostServicesFallbackPreservesCallerPriorityAndServiceIdentity() {
        /** 原 Engine 显式拥有、必须保持优先级的 system action。 */
        val originalAction = RecordingSystemActionCapability()
        /** fallback 中同名、因此绝不能被调用的 system action。 */
        val fallbackAction = RecordingSystemActionCapability()
        /** fallback 唯一补入的内存 saved-state capability。 */
        val savedState = RecordingSavedStateCapability()
        /** 避免纯 JVM 测试解析 Android Choreographer 的手动调度器。 */
        val scheduler = ManualFrameScheduler()
        /** 只显式配置 system action 的原 Engine。 */
        val originalEngine = PixelEngine.Builder()
            .frameScheduler(scheduler)
            .hostServices(PixelHostCapabilitySet(systemActions = originalAction))
            .build()
        /** 用 fallback 补齐 saved state 后得到的新不可变装配。 */
        val mergedEngine = originalEngine.withHostServicesFallback(
            PixelHostCapabilitySet(
                savedState = savedState,
                systemActions = fallbackAction,
            ),
        )

        assertNotSame(originalEngine, mergedEngine)
        assertSame(originalEngine.services.clock, mergedEngine.services.clock)
        assertSame(originalEngine.services.frameScheduler, mergedEngine.services.frameScheduler)
        assertSame(originalEngine.services.resourceCache, mergedEngine.services.resourceCache)
        assertSame(
            PixelCapabilityResult.Handled,
            mergedEngine.services.hostServices.dispatchSystemAction(PixelNavigateBackAction),
        )
        assertEquals(1, originalAction.dispatchCount)
        assertEquals(0, fallbackAction.dispatchCount)
        assertSame(
            PixelCapabilityResult.Handled,
            mergedEngine.services.hostServices.saveState("screen", byteArrayOf(1, 2, 3)),
        )
        assertTrue(originalEngine.services.hostServices.restoreState("screen") is PixelCapabilityValueResult.Unsupported)
        /** 合并 Engine 应能读回 fallback 保存的独立副本。 */
        val restored = mergedEngine.services.hostServices.restoreState("screen")
        assertTrue(restored is PixelCapabilityValueResult.Value)
        assertTrue((restored as PixelCapabilityValueResult.Value).value!!.contentEquals(byteArrayOf(1, 2, 3)))
    }

    /** 提供固定数值、无需 Android SystemClock 的 fake 时钟。 */
    private class FakeClock(
        /** 固定单调毫秒。 */
        private val uptimeMillis: Long,
        /** 固定单调纳秒。 */
        private val nanoTime: Long,
        /** 固定墙上毫秒。 */
        private val wallTimeMillis: Long,
    ) : PixelClock {
        /** 返回固定单调毫秒。 */
        override fun uptimeMillis(): Long = uptimeMillis

        /** 返回固定单调纳秒。 */
        override fun nanoTime(): Long = nanoTime

        /** 返回固定墙上毫秒。 */
        override fun currentTimeMillis(): Long = wallTimeMillis
    }

    /** 记录类型安全系统动作分发次数的 fake capability。 */
    private class RecordingSystemActionCapability : PixelSystemActionCapability {
        /** 已接收动作数量。 */
        var dispatchCount: Int = 0
            private set

        /** 记录动作并声明已处理。 */
        override fun dispatch(action: com.purride.pixelui.PixelTypedSystemAction): PixelCapabilityResult {
            dispatchCount += 1
            return PixelCapabilityResult.Handled
        }
    }

    /** 纯 JVM fallback 测试使用的最小内存 saved-state capability。 */
    private class RecordingSavedStateCapability : PixelSavedStateCapability {
        /** 当前 key 到防御性字节副本的内存映射。 */
        private val values: MutableMap<String, ByteArray> = mutableMapOf()

        /** 返回保存值的防御性副本。 */
        override fun restore(key: String): ByteArray? = values[key]?.copyOf()

        /** 保存字节副本并声明处理成功。 */
        override fun save(key: String, value: ByteArray): PixelCapabilityResult {
            values[key] = value.copyOf()
            return PixelCapabilityResult.Handled
        }

        /** 幂等删除指定 key。 */
        override fun remove(key: String): PixelCapabilityResult {
            values.remove(key)
            return PixelCapabilityResult.Handled
        }
    }
}
