package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelengine.PixelEngine
import com.purride.pixelengine.PixelResourceResolution
import com.purride.pixelengine.PixelResourceRequest
import com.purride.pixelcore.PixelResourceKind
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.services.PixelClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** 在真实 Android Host 上验证 Engine 服务、主题和 capability 隔离。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelEngineServicesInstrumentedTest {
    /** 两个同时 attach 的 Host 只读取各自绑定 Engine 的服务。 */
    @Test
    fun twoHostsKeepEngineThemeClockCacheAndCapabilitiesIsolated() {
        /** 第一 Host 的构建期观察结果。 */
        val firstProbe = EngineServiceProbe()
        /** 第二 Host 的构建期观察结果。 */
        val secondProbe = EngineServiceProbe()
        /** 第一 Engine 的类型安全动作记录器。 */
        val firstActions = RecordingActionCapability()
        /** 第二 Engine 的类型安全动作记录器。 */
        val secondActions = RecordingActionCapability()
        /** 第一 Engine 使用的手动帧源。 */
        val firstScheduler = ManualFrameScheduler()
        /** 第二 Engine 使用的手动帧源。 */
        val secondScheduler = ManualFrameScheduler()
        /** 第一 Engine 的完整不可变配置。 */
        val firstEngine = PixelEngine.Builder()
            .clock(FixedClock(11L))
            .frameScheduler(firstScheduler)
            .resourceResolver { request ->
                PixelResourceResolution.Resolved("first/${request.key}")
            }
            .hostCapabilities(HostCapabilitiesData(density = 1f, refreshRateHz = 60f))
            .hostServices(PixelHostCapabilitySet(systemActions = firstActions))
            .theme(PixelThemeTokens.Dark)
            .build()
        /** 第二 Engine 的完整不可变配置。 */
        val secondEngine = PixelEngine.Builder()
            .clock(FixedClock(22L))
            .frameScheduler(secondScheduler)
            .resourceResolver { request ->
                PixelResourceResolution.Resolved("second/${request.key}")
            }
            .hostCapabilities(HostCapabilitiesData(density = 2f, refreshRateHz = 120f))
            .hostServices(PixelHostCapabilitySet(systemActions = secondActions))
            .theme(PixelThemeTokens.Light)
            .build()

        ActivityScenario.launch(PixelHostFragmentTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** 只绑定第一 Engine 的 Host。 */
                val firstHost = PixelHostView(activity).bindEngine(firstEngine).apply {
                    setContent { EngineServiceProbeWidget(firstProbe) }
                }
                /** 只绑定第二 Engine 的 Host。 */
                val secondHost = PixelHostView(activity).bindEngine(secondEngine).apply {
                    setContent { EngineServiceProbeWidget(secondProbe) }
                }
                activity.rootView.addView(firstHost, fullSizeLayoutParams())
                activity.rootView.addView(secondHost, fullSizeLayoutParams())
                renderSynchronously(firstHost)
                renderSynchronously(secondHost)

                assertSame(firstEngine, firstHost.engine)
                assertSame(secondEngine, secondHost.engine)
                assertSame(firstScheduler, firstHost.frameScheduler)
                assertSame(secondScheduler, secondHost.frameScheduler)
                assertNotSame(firstHost.tickerProvider, secondHost.tickerProvider)
                assertNotSame(
                    firstEngine.services.resourceCache,
                    secondEngine.services.resourceCache,
                )
                assertEquals(PixelThemeTokens.Dark, firstProbe.theme)
                assertEquals(PixelThemeTokens.Light, secondProbe.theme)
                assertEquals(1f, firstProbe.capabilities?.density)
                assertEquals(2f, secondProbe.capabilities?.density)
                assertEquals(11L, firstEngine.services.clock.nanoTime())
                assertEquals(22L, secondEngine.services.clock.nanoTime())
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
                    firstProbe.hostServices.dispatchSystemAction(PixelNavigateBackAction),
                )
                assertEquals(1, firstActions.dispatchCount)
                assertEquals(0, secondActions.dispatchCount)
                assertSame(
                    PixelCapabilityResult.Handled,
                    secondProbe.hostServices.dispatchSystemAction(PixelNavigateBackAction),
                )
                assertEquals(1, firstActions.dispatchCount)
                assertEquals(1, secondActions.dispatchCount)

                firstHost.destroy()
                secondHost.destroy()
            }
        }
    }

    /** 在 Activity 主线程同步绘制一帧。 */
    private fun renderSynchronously(host: PixelHostView) {
        /** 为 Host draw 提供的临时 Canvas backing bitmap。 */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    /** 创建填满测试容器的 Host 布局参数。 */
    private fun fullSizeLayoutParams(): ViewGroup.LayoutParams {
        return ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

/** 记录一个 Host 根树实际观察到的 Engine 环境。 */
private class EngineServiceProbe {
    /** 最近观察到的主题。 */
    var theme: PixelThemeTokens? = null

    /** 最近观察到的完整环境。 */
    var capabilities: HostCapabilitiesData? = null

    /** 最近观察到的聚焦 Host capability。 */
    var hostServices: PixelHostCapabilitySet = PixelHostCapabilitySet.Empty
}

/** 在 build 阶段读取当前 Host 的 Engine 环境。 */
private class EngineServiceProbeWidget(
    /** 接收观察结果的 Host 私有记录器。 */
    private val probe: EngineServiceProbe,
) : StatelessWidget() {
    /** 记录 inherited 环境并返回最小可绘制内容。 */
    override fun build(context: BuildContext): Widget {
        probe.theme = PixelTheme.of(context)
        probe.capabilities = HostCapabilities.of(context)
        probe.hostServices = PixelHostServices.of(context)
        return Text("ENGINE")
    }
}

/** 提供固定单调时间的 fake Engine 时钟。 */
private class FixedClock(
    /** 固定单调纳秒。 */
    private val nanos: Long,
) : PixelClock {
    /** 把固定纳秒换算为毫秒。 */
    override fun uptimeMillis(): Long = nanos / 1_000_000L

    /** 返回固定单调纳秒。 */
    override fun nanoTime(): Long = nanos

    /** 测试不使用墙上时间，返回零。 */
    override fun currentTimeMillis(): Long = 0L
}

/** 记录一个 Engine 收到的类型安全系统动作。 */
private class RecordingActionCapability : PixelSystemActionCapability {
    /** 当前已处理动作数量。 */
    var dispatchCount: Int = 0
        private set

    /** 记录动作并返回已处理。 */
    override fun dispatch(action: PixelTypedSystemAction): PixelCapabilityResult {
        dispatchCount += 1
        return PixelCapabilityResult.Handled
    }
}
