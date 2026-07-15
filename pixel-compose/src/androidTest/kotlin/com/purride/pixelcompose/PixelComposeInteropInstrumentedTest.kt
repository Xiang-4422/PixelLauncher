package com.purride.pixelcompose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.purride.pixelui.PixelCapabilityResult
import com.purride.pixelui.PixelHostAttachmentState
import com.purride.pixelui.PixelHostLifecycleOwnerBinding
import com.purride.pixelui.PixelHostLifecycleState
import com.purride.pixelui.PixelTextInputBridge
import com.purride.pixelui.PixelTextInputEvent
import com.purride.pixelui.PixelTextInputRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 在 API 30+ 真实 Compose AndroidView 树中验收 Pixel Host 互操作。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class PixelComposeInteropInstrumentedTest {
    /** 每个用例清除进程内探针，确保 restored 证据只来自当前 Activity saved state。 */
    @Before
    fun resetProbe() {
        PixelComposeInteropProbe.reset()
        PixelComposeInteropTestActivity.resetCreationCount()
    }

    /**
     * Compose wrapper 必须传递 lifecycle、density、insets、focus、IME、accessibility 与 saved state。
     */
    @Test
    fun propagatesHostEnvironmentAndRestoresStateAcrossActivityRecreation() {
        /** 当前 instrumentation，用于等待 Compose 和 AndroidView 完成异步提交。 */
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        /** 重建前的 Host 引用，用于验证旧 owner 已终结。 */
        var originalHost: com.purride.pixelui.PixelHostView? = null

        ActivityScenario.launch(PixelComposeInteropTestActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                /** Compose AndroidView 中已经 attach 的真实 Pixel Host。 */
                val host = activity.hostView
                originalHost = host
                renderSynchronously(host)

                assertEquals(PixelHostAttachmentState.Attached, host.lifecycleDiagnostics.attachmentState)
                assertEquals(PixelHostLifecycleState.Resumed, host.lifecycleDiagnostics.lifecycleState)
                assertEquals(PixelHostLifecycleOwnerBinding.ViewTree, host.lifecycleDiagnostics.ownerBinding)
                assertTrue(host.lifecycleDiagnostics.isInteractive)
                assertEquals(activity.resources.displayMetrics.density, PixelComposeInteropProbe.density!!, 0f)
                assertEquals(PixelCapabilityResult.Handled, PixelComposeInteropProbe.initialSaveResult)

                /** Compose wrapper 私有容器，负责把 WindowInsets 转发给内部 Host。 */
                val composeContainer = host.parent?.parent as PixelComposeHostContainer
                /** 大于当前格点尺寸的稳定系统栏物理 inset。 */
                val platformInsets = WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(), Insets.of(32, 48, 24, 40))
                    .build()
                composeContainer.dispatchApplyWindowInsets(platformInsets)
                assertTrue(host.windowInsets.left > 0)
                assertTrue(host.windowInsets.top > 0)
                assertTrue(host.windowInsets.right > 0)
                assertTrue(host.windowInsets.bottom > 0)

                assertTrue(host.requestFocus())
                assertTrue(host.hasFocus())
                assertTrue(host.dispatchPixelTextInput(PixelTextInputEvent("像素🙂")))
                assertEquals(listOf("像素🙂"), activity.receivedTextInputs)

                /** 默认 setup 创建并与 Compose ViewTree 共同 attach 的真实 IME 桥。 */
                val textInputBridge = host.hostBridge as PixelTextInputBridge
                textInputBridge.showTextInput(PixelTextInputRequest(text = "IME"))
                assertTrue(textInputBridge.inputView.hasFocus())
                textInputBridge.hideTextInput()
                assertFalse(textInputBridge.inputView.hasFocus())

                /** Pixel virtual semantics 必须继续作为 AndroidView 的无障碍子树发布。 */
                val hostNode = host.accessibilityNodeProvider
                    .createAccessibilityNodeInfo(View.NO_ID)
                assertNotNull(hostNode)
                assertTrue(hostNode!!.childCount > 0)
            }

            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                /** Activity 重建后 Compose 创建的全新 Host。 */
                val restoredHost = activity.hostView
                renderSynchronously(restoredHost)

                assertTrue(restoredHost !== originalHost)
                assertArrayEquals(PixelComposeInteropProbe.StatePayload, PixelComposeInteropProbe.restoredPayload)
                assertEquals(PixelHostLifecycleState.Resumed, restoredHost.lifecycleDiagnostics.lifecycleState)
                assertTrue(restoredHost.lifecycleDiagnostics.isInteractive)
            }
        }

        assertEquals(PixelHostLifecycleState.Destroyed, originalHost!!.lifecycleDiagnostics.lifecycleState)
    }

    /** saved-state capability 必须防御性复制，并把超限值转换为结构化失败结果。 */
    @Test
    fun savedStateCapabilityIsDefensiveAndBounded() {
        /** 直接使用与 Composable 相同实现的独立状态存储。 */
        val capability = PixelComposeSavedStateCapability()
        /** 写入后会被调用方修改的源数组。 */
        val source = byteArrayOf(1, 2, 3)

        assertEquals(PixelCapabilityResult.Handled, capability.save("state", source))
        source[0] = 9
        /** 第一次恢复得到的防御性副本。 */
        val firstRestore = capability.restore("state")!!
        assertArrayEquals(byteArrayOf(1, 2, 3), firstRestore)
        firstRestore[1] = 8
        assertArrayEquals(byteArrayOf(1, 2, 3), capability.restore("state"))

        /** 超过单值上限一字节的拒绝负载。 */
        val oversized = ByteArray(256 * 1024 + 1)
        assertTrue(capability.save("oversized", oversized) is PixelCapabilityResult.Failed)
        assertEquals(PixelCapabilityResult.Handled, capability.remove("state"))
        assertEquals(null, capability.restore("state"))
    }

    /** 同步绘制一帧，使 retained build、焦点和 semantics 都提交到 Host。 */
    private fun renderSynchronously(host: com.purride.pixelui.PixelHostView) {
        /** 提供确定尺寸与 Canvas 的一次性 bitmap。 */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }
}
