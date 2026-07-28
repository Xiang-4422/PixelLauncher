package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.internal.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** API 37 真实 [PixelHostView] 上的 M4-3 安全定位、路由顺序与资源终态验收。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 37)
class PixelHostProductionOverlayInstrumentedTest {
    /**
     * 四角锚点在横竖逻辑屏、稳定 window inset 与 IME inset 下始终留在安全视口，
     * 并且真实 Android [MotionEvent] 能命中浮层表面动作并完成受控关闭。
     */
    @Test
    fun popoverFourCornersRelayoutInsideSafeViewportAndSurfaceActionCloses() {
        /** 每个受控 Popover 当前是否仍处于逻辑打开状态。 */
        val expandedByCorner = linkedMapOf(
            TopLeftLabel to true,
            TopRightLabel to true,
            BottomLeftLabel to true,
            BottomRightLabel to true,
        )
        /** 真实浮层表面动作成功关闭的次数。 */
        var surfaceCloseCount = 0

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** 已 attach、resumed 且具有真实 Android Window 的被测 Host。 */
                val host = activity.hostView
                host.motionSettingsOverride = PixelMotionSettings(animatorDurationScale = 0f)
                host.setWindowInsets(left = 3, top = 4, right = 5, bottom = 6)
                host.setViewInsets(bottom = 14)
                host.setContent {
                    fourCornerPopoverFixture(
                        expandedByCorner = expandedByCorner,
                        onSurfaceClose = { label ->
                            expandedByCorner[label] = false
                            surfaceCloseCount += 1
                        },
                    )
                }

                /** 先以窄而高的逻辑屏验证四角 collision/flip 与 IME 顶边。 */
                host.profilePolicy = PixelHostProfilePolicy.Fixed(ScreenProfile(logicalWidth = 48, logicalHeight = 72, dotSizePx = 1))
                renderSynchronously(host)
                assertFourPopoversInsideSafeViewport(host)

                /** 再横竖交换逻辑尺寸，并改变两类 inset，验证同一 retained 状态重新布局。 */
                host.profilePolicy = PixelHostProfilePolicy.Fixed(ScreenProfile(logicalWidth = 72, logicalHeight = 48, dotSizePx = 1))
                host.setWindowInsets(left = 2, top = 3, right = 7, bottom = 5)
                host.setViewInsets(left = 4, bottom = 10)
                renderSynchronously(host)
                assertFourPopoversInsideSafeViewport(host)

                /** 横屏重排后右下角浮层表面的真实语义矩形。 */
                val bottomRightBounds = semanticsBounds(host, BottomRightLabel)
                tapLogicalBounds(host = host, bounds = bottomRightBounds)
                renderSynchronously(host)

                assertEquals(1, surfaceCloseCount)
                assertFalse(expandedByCorner.getValue(BottomRightLabel))
                assertTrue(
                    host.lastRenderResult?.semanticsNodes.orEmpty().none { node ->
                        node.label == BottomRightLabel
                    },
                )
                /** 其余三个 retained Popover 在一次同树局部关闭后仍须保持安全定位。 */
                RemainingCornerLabels.forEach { label -> assertSemanticsInsideSafeViewport(host, label) }
            }
        }
    }

    /**
     * 多层 route 在真实 Host 上依次经历系统 Back、表面 typed completion 与外部 barrier；
     * route 焦点随 canonical 层级交接，outcome 顺序稳定且最终无 Host 资源残留。
     */
    @Test
    fun popupRoutesKeepOutcomeOrderAndLeaveNoHostResidueAfterBackBarrierAndCompletion() {
        /** 手动帧源用于证明 route 关闭后没有 ticker 或上游 callback 遗留。 */
        val scheduler = ManualFrameScheduler()
        /** Host 返回链与 [PixelOverlayHost] 共用的 dispatcher。 */
        val backDispatcher = PixelBackDispatcher()
        /** 同时承载 Modal 与 System 层 route 的统一控制器。 */
        val overlayController = PixelOverlayController()
        /** 最上层到最下层的实际 outcome callback 顺序。 */
        val outcomeOrder = mutableListOf<String>()
        /** Typed route 内的 autofocus 节点，用于观察 presentation 卸载后的焦点释放。 */
        val typedFocusNode = FocusNode("m4-3-typed-route")
        /** 最高非模态 route 的 autofocus 节点，用于验证焦点顺序与低层 modal 一致。 */
        val systemFocusNode = FocusNode("m4-3-system-route")

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** 已 attach、resumed 且使用真实 View 输入管线的被测 Host。 */
                val host = activity.hostView
                host.frameScheduler = scheduler
                host.motionSettingsOverride = PixelMotionSettings(animatorDurationScale = 0f)
                host.profilePolicy = PixelHostProfilePolicy.Fixed(ScreenProfile(logicalWidth = 64, logicalHeight = 40, dotSizePx = 1))
                host.backDispatcher = backDispatcher
                host.setContent {
                    PixelBackHost(
                        dispatcher = backDispatcher,
                        child = PixelOverlayHost(
                            controller = overlayController,
                            child = Semantics(
                                label = BackgroundLabel,
                                child = Container(
                                    width = 64,
                                    height = 40,
                                    fillColor = PixelColor.fromRgb(8, 12, 16),
                                    borderColor = null,
                                ),
                            ),
                        ),
                    )
                }
                renderSynchronously(host)

                /** 无浮层时 retained Element 拓扑的基准。 */
                val baselineElementTree = host.dumpElementTree()
                /** 无浮层时 render object 拓扑的基准。 */
                val baselineRenderTree = host.dumpRenderTree()
                /** 无浮层时 Host target 与 semantics 数量的基准。 */
                val baselineTargetCounts = host.inspect(includeFrameStats = false).targetCounts

                /** 最下层 modal；最后通过真实外部 barrier 点击关闭。 */
                val barrierEntry = overlayController.show(
                    PixelPopupRoute<Unit>(
                        content = Dialog(
                            content = Text("LOWER BODY"),
                            key = "m4-3-lower-dialog",
                            semanticLabel = LowerDialogLabel,
                        ),
                        layer = PixelOverlayLayer.Modal,
                        barrier = PixelOverlayBarrier(color = PixelColor.Transparent),
                        modal = true,
                        onOutcome = { outcome ->
                            outcomeOrder += outcomeTag(prefix = "lower", outcome = outcome)
                        },
                    ),
                )
                /** 先声明 typed entry，使表面 action 能完成它自身而不是旁路 controller。 */
                lateinit var typedEntry: PixelOverlayEntry<String>
                /** 中层 typed modal；按钮通过真实 MotionEvent 返回业务结果。 */
                typedEntry = overlayController.show(
                    PixelPopupRoute(
                        content = Focus(
                            node = typedFocusNode,
                            autofocus = true,
                            child = Dialog(
                                content = Text("TYPE A RESULT"),
                                actions = listOf(
                                    OutlinedButton(
                                        text = CompleteActionLabel,
                                        onPressed = { typedEntry.complete(TypedResult) },
                                        key = CompleteActionKey,
                                    ),
                                ),
                                key = "m4-3-typed-dialog",
                                semanticLabel = TypedDialogLabel,
                            ),
                        ),
                        layer = PixelOverlayLayer.Modal,
                        barrier = PixelOverlayBarrier(color = PixelColor.Transparent),
                        modal = true,
                        onOutcome = { outcome ->
                            outcomeOrder += outcomeTag(prefix = "typed", outcome = outcome)
                        },
                    ),
                )
                /** 最高 System route；系统 Back 必须先命中它，即使 modal focus 在中层。 */
                val systemEntry = overlayController.show(
                    PixelPopupRoute<Unit>(
                        content = Focus(
                            node = systemFocusNode,
                            autofocus = true,
                            child = Positioned(
                                left = 1,
                                top = 1,
                                child = Semantics(
                                    label = SystemRouteLabel,
                                    child = PixelOverlaySurface(
                                        child = Container(
                                            width = 10,
                                            height = 6,
                                            fillColor = PixelColor.fromRgb(40, 180, 90),
                                            borderColor = null,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        layer = PixelOverlayLayer.System,
                        modal = false,
                        onOutcome = { outcome ->
                            outcomeOrder += outcomeTag(prefix = "system", outcome = outcome)
                        },
                    ),
                )
                renderSynchronously(host)

                assertTrue(systemFocusNode.isFocused)
                assertFalse(typedFocusNode.isFocused)
                assertEquals(1, host.lastRenderResult?.semanticsNodes.orEmpty().count { node ->
                    node.label == SystemRouteLabel
                })

                assertTrue(host.handleBackPressed())
                assertEquals(PixelOverlayLifecycle.Removing, systemEntry.lifecycle)
                renderSynchronously(host)
                assertEquals(PixelOverlayLifecycle.Disposed, systemEntry.lifecycle)
                assertFalse(systemFocusNode.isFocused)
                assertTrue(typedFocusNode.isFocused)
                assertEquals(listOf("system:dismissed:Back"), outcomeOrder)

                /** 焦点交接后 typed footer 的真实 Host 语义边界。 */
                val completeBounds = semanticsBounds(host, CompleteActionLabel)
                tapLogicalBounds(host = host, bounds = completeBounds)
                assertEquals(PixelOverlayLifecycle.Removing, typedEntry.lifecycle)
                assertEquals(listOf("system:dismissed:Back"), outcomeOrder)

                /** 下一真实 Host 帧卸载 typed presentation 并按逻辑关闭顺序派发结果。 */
                renderSynchronously(host)
                assertEquals(
                    listOf(
                        "system:dismissed:Back",
                        "typed:completed:$TypedResult",
                    ),
                    outcomeOrder,
                )
                assertEquals(PixelOverlayLifecycle.Disposed, systemEntry.lifecycle)
                assertEquals(PixelOverlayLifecycle.Disposed, typedEntry.lifecycle)
                assertFalse(typedFocusNode.isFocused)

                /** 左上角不与居中 lower Dialog 表面重叠，因此真实触摸命中其外部 barrier。 */
                tapLogicalPoint(host = host, logicalX = 1f, logicalY = 1f)
                assertEquals(PixelOverlayLifecycle.Removing, barrierEntry.lifecycle)
                renderSynchronously(host)

                assertEquals(
                    listOf(
                        "system:dismissed:Back",
                        "typed:completed:$TypedResult",
                        "lower:dismissed:Barrier",
                    ),
                    outcomeOrder,
                )
                assertEquals(PixelOverlayLifecycle.Disposed, barrierEntry.lifecycle)
                assertEquals(0, overlayController.size)

                /** 多绘一帧确保 retained teardown 已发布为最终 Host 快照。 */
                renderSynchronously(host)
                /** 清理后的只读 Host 诊断快照。 */
                val cleanSnapshot = host.inspect(includeFrameStats = false)
                /** 清理后的 frame/ticker 诊断快照。 */
                val frameDiagnostics = host.frameScopeDiagnostics
                assertEquals(baselineElementTree, host.dumpElementTree())
                assertEquals(baselineRenderTree, host.dumpRenderTree())
                assertEquals(baselineTargetCounts, cleanSnapshot.targetCounts)
                assertFalse(cleanSnapshot.hasPendingBuild)
                assertFalse(cleanSnapshot.focusedTextInput)
                assertEquals(0, frameDiagnostics.pendingCallbackCount)
                assertEquals(0, frameDiagnostics.frameListenerCount)
                assertEquals(0, frameDiagnostics.activeTickerCount)
                assertEquals(0, frameDiagnostics.liveTickerCount)
                assertFalse(frameDiagnostics.sourceFramePending)
                assertEquals(0, scheduler.pendingCount)
                assertEquals(
                    1,
                    host.lastRenderResult?.semanticsNodes.orEmpty().count { node ->
                        node.label == BackgroundLabel
                    },
                )
                assertTrue(
                    host.lastRenderResult?.semanticsNodes.orEmpty().none { node ->
                        node.label in OverlayLabels
                    },
                )
                assertTrue("instrumentation API=${Build.VERSION.SDK_INT}", Build.VERSION.SDK_INT >= 37)
            }
        }
    }

    /** 构建四个锚点均位于 Host 边角的受控、可点击非模态 Popover。 */
    private fun fourCornerPopoverFixture(
        expandedByCorner: Map<String, Boolean>,
        onSurfaceClose: (String) -> Unit,
    ): Widget {
        /** 每个角的固定 anchor/presentation，right/bottom 会随 screenProfile 自动重算。 */
        val children = listOf(
            Positioned(
                left = 0,
                top = 0,
                child = cornerPopover(TopLeftLabel, expandedByCorner.getValue(TopLeftLabel), onSurfaceClose),
            ),
            Positioned(
                right = 0,
                top = 0,
                child = cornerPopover(TopRightLabel, expandedByCorner.getValue(TopRightLabel), onSurfaceClose),
            ),
            Positioned(
                left = 0,
                bottom = 0,
                child = cornerPopover(BottomLeftLabel, expandedByCorner.getValue(BottomLeftLabel), onSurfaceClose),
            ),
            Positioned(
                right = 0,
                bottom = 0,
                child = cornerPopover(BottomRightLabel, expandedByCorner.getValue(BottomRightLabel), onSurfaceClose),
            ),
        )
        return Stack(children = children)
    }

    /** 创建一个使用真实 anchor bounds、Auto flip 与固定表面尺寸的 Popover。 */
    private fun cornerPopover(
        label: String,
        expanded: Boolean,
        onSurfaceClose: (String) -> Unit,
    ): Widget {
        /** 真实指针动作与语义矩形共用的 Popover 表面。 */
        val surface = Semantics(
            label = label,
            child = GestureDetector(
                onTap = { onSurfaceClose(label) },
                key = "$label-action",
                child = Container(
                    width = 12,
                    height = 8,
                    fillColor = PixelColor.fromRgb(30, 120, 220),
                    borderColor = PixelColor.White,
                ),
            ),
        )
        return Popover(
            anchor = Container(
                width = 4,
                height = 4,
                fillColor = PixelColor.White,
                borderColor = null,
            ),
            content = surface,
            expanded = expanded,
            contentOffset = IntOffset(0, 5),
            modal = false,
            placement = PixelPopoverPlacement.Auto,
            alignment = PixelPopoverAlignment.Start,
            viewportMargin = 1,
            key = label,
        )
    }

    /** 验证当前 Host 帧里的四个 Popover 均落在 window/IME 合并后的安全视口。 */
    private fun assertFourPopoversInsideSafeViewport(host: PixelHostView) {
        AllCornerLabels.forEach { label -> assertSemanticsInsideSafeViewport(host, label) }
    }

    /** 验证指定语义矩形没有越过当前 Host 的合并安全边界和 Popover 1px margin。 */
    private fun assertSemanticsInsideSafeViewport(host: PixelHostView, label: String) {
        /** 当前标签唯一导出的 Popover 表面矩形。 */
        val bounds = semanticsBounds(host, label)
        /** 稳定 inset 与临时 inset 逐边取大值后再加入 Popover margin。 */
        val safeLeft = maxOf(host.windowInsets.left, host.viewInsets.left) + 1
        /** 稳定 inset 与临时 inset 逐边取大值后再加入 Popover margin。 */
        val safeTop = maxOf(host.windowInsets.top, host.viewInsets.top) + 1
        /** 当前逻辑宽度扣除右侧合并 inset 与 Popover margin 后的 exclusive right。 */
        val safeRight = host.screenProfile.logicalWidth - maxOf(host.windowInsets.right, host.viewInsets.right) - 1
        /** 当前逻辑高度扣除底部合并 inset 与 Popover margin 后的 exclusive bottom。 */
        val safeBottom = host.screenProfile.logicalHeight - maxOf(host.windowInsets.bottom, host.viewInsets.bottom) - 1
        assertTrue("$label left=${bounds.left} safeLeft=$safeLeft", bounds.left >= safeLeft)
        assertTrue("$label top=${bounds.top} safeTop=$safeTop", bounds.top >= safeTop)
        assertTrue("$label right=${bounds.right} safeRight=$safeRight", bounds.right <= safeRight)
        assertTrue("$label bottom=${bounds.bottom} safeBottom=$safeBottom", bounds.bottom <= safeBottom)
    }

    /** 从最近一次真实 Host 渲染结果中取得唯一标签的逻辑矩形。 */
    private fun semanticsBounds(host: PixelHostView, label: String): PixelRect {
        /** 当前帧中与指定标签完全匹配的语义节点。 */
        val node = host.lastRenderResult?.semanticsNodes.orEmpty().single { candidate ->
            candidate.label == label
        }
        return PixelRect(
            left = node.left,
            top = node.top,
            width = node.width,
            height = node.height,
        )
    }

    /** 把逻辑矩形中心映射到真实 Android View 坐标并发送完整触摸序列。 */
    private fun tapLogicalBounds(host: PixelHostView, bounds: PixelRect) {
        /** 保持在矩形内部的逻辑横坐标。 */
        val logicalX = bounds.left + (bounds.width.coerceAtLeast(1) - 1) / 2f
        /** 保持在矩形内部的逻辑纵坐标。 */
        val logicalY = bounds.top + (bounds.height.coerceAtLeast(1) - 1) / 2f
        tapLogicalPoint(host = host, logicalX = logicalX, logicalY = logicalY)
    }

    /** 将一个逻辑像素点转换到实际 View 空间后，经 [PixelHostView.onTouchEvent] 点击。 */
    private fun tapLogicalPoint(host: PixelHostView, logicalX: Float, logicalY: Float) {
        /** 当前 screenProfile 对应的 View 原点与物理 cell 大小。 */
        val geometry = checkNotNull(host.resolveGridGeometry())
        /** 逻辑像素中心映射得到的 Android View 横坐标。 */
        val rawX = geometry.originX + (logicalX + 0.5f) * geometry.cellSize
        /** 逻辑像素中心映射得到的 Android View 纵坐标。 */
        val rawY = geometry.originY + (logicalY + 0.5f) * geometry.cellSize
        /** 单次点击共享的真实 Android uptime 起点。 */
        val downTime = SystemClock.uptimeMillis()
        /** 触摸按下事件。 */
        val down = touchEvent(
            action = MotionEvent.ACTION_DOWN,
            x = rawX,
            y = rawY,
            downTime = downTime,
            eventTime = downTime,
        )
        /** 触摸抬起事件。 */
        val up = touchEvent(
            action = MotionEvent.ACTION_UP,
            x = rawX,
            y = rawY,
            downTime = downTime,
            eventTime = downTime + 16L,
        )
        try {
            assertTrue(host.onTouchEvent(down))
            assertTrue(host.onTouchEvent(up))
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    /** 创建来源明确为触摸屏的 Android [MotionEvent]。 */
    private fun touchEvent(
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long,
    ): MotionEvent {
        return MotionEvent.obtain(downTime, eventTime, action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
    }

    /** 把公开 typed outcome 转成紧凑且可精确断言的顺序标签。 */
    private fun outcomeTag(prefix: String, outcome: PixelOverlayOutcome<*>): String {
        return when (outcome) {
            is PixelOverlayOutcome.Completed<*> -> "$prefix:completed:${outcome.result}"
            is PixelOverlayOutcome.Dismissed -> "$prefix:dismissed:${outcome.reason}"
        }
    }

    /** 在 Activity 主线程同步绘制一帧，使 View target、semantics 与 retained teardown 同步可见。 */
    private fun renderSynchronously(host: PixelHostView) {
        /** 与当前真实 View 尺寸一致的临时目标位图。 */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    /** 测试标签、业务结果与稳定集合。 */
    private companion object {
        /** 左上锚点 Popover 的唯一语义标签。 */
        const val TopLeftLabel: String = "M4-3 TOP LEFT"

        /** 右上锚点 Popover 的唯一语义标签。 */
        const val TopRightLabel: String = "M4-3 TOP RIGHT"

        /** 左下锚点 Popover 的唯一语义标签。 */
        const val BottomLeftLabel: String = "M4-3 BOTTOM LEFT"

        /** 右下锚点 Popover 的唯一语义标签。 */
        const val BottomRightLabel: String = "M4-3 BOTTOM RIGHT"

        /** 无浮层时保留的应用背景语义标签。 */
        const val BackgroundLabel: String = "M4-3 BACKGROUND"

        /** 最下层 Dialog 的唯一语义标签。 */
        const val LowerDialogLabel: String = "M4-3 LOWER DIALOG"

        /** Typed Dialog 的唯一语义标签。 */
        const val TypedDialogLabel: String = "M4-3 TYPED DIALOG"

        /** Typed footer action 的显示和语义标签。 */
        const val CompleteActionLabel: String = "COMPLETE ROUTE"

        /** Typed footer action 的稳定 retained key。 */
        const val CompleteActionKey: String = "m4-3-complete-action"

        /** 最高 System route 的唯一语义标签。 */
        const val SystemRouteLabel: String = "M4-3 SYSTEM ROUTE"

        /** Typed completion 应返回的业务值。 */
        const val TypedResult: String = "accepted"

        /** 四个角的完整固定顺序。 */
        val AllCornerLabels: List<String> = listOf(
            TopLeftLabel,
            TopRightLabel,
            BottomLeftLabel,
            BottomRightLabel,
        )

        /** 关闭右下角后仍应保留的三个角。 */
        val RemainingCornerLabels: List<String> = listOf(
            TopLeftLabel,
            TopRightLabel,
            BottomLeftLabel,
        )

        /** 清理后不得再出现在 Host semantics 中的所有 overlay 标签。 */
        val OverlayLabels: Set<String> = setOf(
            LowerDialogLabel,
            TypedDialogLabel,
            CompleteActionLabel,
            SystemRouteLabel,
            "Dismiss",
        )
    }
}
