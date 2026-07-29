package com.purride.pixelui.widgets

import com.purride.pixelui.Container
import com.purride.pixelui.Dialog
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelKey
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * modal 表面的内建关闭屏障契约。
 *
 * 提供 onDismissRequest 时，Back 与「点击表面之外」都请求关闭；不提供时 modal
 * 继续拦截两者。屏障必须位于 modal 作用域内部——作用域之外的命中目标会被
 * 过滤，调用方在浮层下方自行铺的遮罩收不到任何点击。
 */
class PixelModalDismissBarrierTest {

    @Test
    fun outsideTapRequestsDismissWhenCallbackProvided() {
        var dismissed = 0
        val tester = PixelTester()
        tester.pumpWidget(
            widget = modalRoot(onDismissRequest = { dismissed += 1 }),
            logicalWidth = CANVAS,
            logicalHeight = CANVAS,
        )

        assertTrue(tapAt(tester, 0, 0))
        assertEquals(1, dismissed)
    }

    @Test
    fun surfaceTapKeepsModalOpen() {
        var dismissed = 0
        var confirmed = 0
        val tester = PixelTester()
        tester.pumpWidget(
            widget = modalRoot(
                onDismissRequest = { dismissed += 1 },
                onConfirm = { confirmed += 1 },
            ),
            logicalWidth = CANVAS,
            logicalHeight = CANVAS,
        )

        // 表面自身的命中目标排在屏障之后，因此表面区域的点击不会被屏障吃掉。
        val surface = surfaceTarget(tester)
        val centerX = (surface.bounds.left + surface.bounds.right) / 2
        val centerY = (surface.bounds.top + surface.bounds.bottom) / 2
        assertTrue(tapAt(tester, centerX, centerY))
        assertEquals(0, dismissed)

        // 浮层内的操作按钮仍然可用。
        val action = tester.renderResult?.clickTargets.orEmpty().last()
        action.onClick.invoke()
        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun backStillRequestsDismiss() {
        var dismissed = 0
        val tester = PixelTester()
        tester.pumpWidget(
            widget = modalRoot(onDismissRequest = { dismissed += 1 }),
            logicalWidth = CANVAS,
            logicalHeight = CANVAS,
        )

        assertTrue(tester.pressKey(PixelKey.BACK))
        assertEquals(1, dismissed)
    }

    @Test
    fun outsideTapIsInertWithoutDismissCallback() {
        var background = 0
        val tester = PixelTester()
        tester.pumpWidget(
            widget = modalRoot(onDismissRequest = null, onBackground = { background += 1 }),
            logicalWidth = CANVAS,
            logicalHeight = CANVAS,
        )

        // 无 dismiss 回调时不装屏障，且背景目标已被 modal 隔离：点击落空而非穿透。
        assertNull(tester.renderResult?.clickTargets?.lastOrNull { it.bounds.contains(0, 0) })
        assertEquals(0, background)
    }

    @Test
    fun dismissOnOutsideTapFalseKeepsBarrierOff() {
        var dismissed = 0
        var background = 0
        val tester = PixelTester()
        tester.pumpWidget(
            widget = modalRoot(
                onDismissRequest = { dismissed += 1 },
                onBackground = { background += 1 },
                dismissOnOutsideTap = false,
            ),
            logicalWidth = CANVAS,
            logicalHeight = CANVAS,
        )

        assertFalse(tapAt(tester, 0, 0))
        assertEquals(0, dismissed)
        assertEquals(0, background)
    }

    @Test
    fun barrierDoesNotLeakBackgroundInteraction() {
        var background = 0
        val tester = PixelTester()
        tester.pumpWidget(
            widget = modalRoot(
                onDismissRequest = { },
                onBackground = { background += 1 },
            ),
            logicalWidth = CANVAS,
            logicalHeight = CANVAS,
        )

        // 屏障接住了表面之外的点击，背景的点击目标不应再出现在本帧。
        assertTrue(tapAt(tester, 0, 0))
        assertEquals(0, background)
    }

    /** 背景可点区 + modal 浮层，对应调用方把 Dialog 叠在页面内容之上的用法。 */
    private fun modalRoot(
        onDismissRequest: (() -> Unit)?,
        onConfirm: () -> Unit = { },
        onBackground: () -> Unit = { },
        dismissOnOutsideTap: Boolean = true,
    ): Widget = Stack(
        children = listOf(
            GestureDetector(
                onTap = onBackground,
                child = Container(),
            ),
            Dialog(
                title = Text("MENU"),
                content = Text("BODY"),
                actions = listOf(OutlinedButton("OK", onPressed = onConfirm)),
                onDismissRequest = onDismissRequest,
                dismissOnOutsideTap = dismissOnOutsideTap,
            ),
        ),
    )

    /** 屏障覆盖整块画布；表面目标是其中更小的那个矩形。 */
    private fun surfaceTarget(tester: PixelTester): PixelClickTarget {
        val targets = tester.renderResult?.clickTargets.orEmpty()
        return targets.first { target -> target.bounds.width < CANVAS }
    }

    /** 复刻宿主的路由规则：命中点上最后（最上层）的目标获得点击。 */
    private fun tapAt(tester: PixelTester, x: Int, y: Int): Boolean {
        val target = tester.renderResult
            ?.clickTargets
            ?.lastOrNull { it.bounds.contains(x, y) }
            ?: return false
        target.onClick.invoke()
        return true
    }

    private companion object {
        const val CANVAS = 96
    }
}
