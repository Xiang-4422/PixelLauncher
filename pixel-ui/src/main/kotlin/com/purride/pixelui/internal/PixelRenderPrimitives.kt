package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import kotlin.math.max

internal data class PixelSize(
    val width: Int,
    val height: Int,
)

internal data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int
        get() = left + width

    val bottom: Int
        get() = top + height

    fun contains(x: Int, y: Int): Boolean {
        return x in left until right && y in top until bottom
    }

    fun inset(paddingLeft: Int, paddingTop: Int, paddingRight: Int, paddingBottom: Int): PixelRect {
        val nextLeft = (left + paddingLeft).coerceAtMost(right)
        val nextTop = (top + paddingTop).coerceAtMost(bottom)
        val nextRight = (right - paddingRight).coerceAtLeast(nextLeft)
        val nextBottom = (bottom - paddingBottom).coerceAtLeast(nextTop)
        return PixelRect(
            left = nextLeft,
            top = nextTop,
            width = nextRight - nextLeft,
            height = nextBottom - nextTop,
        )
    }

    fun translate(deltaX: Int, deltaY: Int): PixelRect {
        return PixelRect(
            left = left + deltaX,
            top = top + deltaY,
            width = width,
            height = height,
        )
    }

    fun intersect(other: PixelRect): PixelRect? {
        val nextLeft = max(left, other.left)
        val nextTop = max(top, other.top)
        val nextRight = minOf(right, other.right)
        val nextBottom = minOf(bottom, other.bottom)
        if (nextRight <= nextLeft || nextBottom <= nextTop) {
            return null
        }
        return PixelRect(
            left = nextLeft,
            top = nextTop,
            width = nextRight - nextLeft,
            height = nextBottom - nextTop,
        )
    }
}

internal data class PixelConstraints(
    val maxWidth: Int,
    val maxHeight: Int,
) {
    fun shrink(
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
    ): PixelConstraints {
        return PixelConstraints(
            maxWidth = (maxWidth - paddingLeft - paddingRight).coerceAtLeast(0),
            maxHeight = (maxHeight - paddingTop - paddingBottom).coerceAtLeast(0),
        )
    }
}

internal data class PixelClickTarget(
    val bounds: PixelRect,
    val onClick: () -> Unit,
)

internal data class PixelPagerTarget(
    val bounds: PixelRect,
    val axis: PixelAxis,
    val state: PixelPagerState,
    val controller: PixelPagerController,
    val onPageChanged: ((Int) -> Unit)?,
)

internal data class PixelListTarget(
    val bounds: PixelRect,
    val viewportHeightPx: Int,
    val contentHeightPx: Int,
    val state: com.purride.pixelui.state.PixelListState,
    val controller: com.purride.pixelui.state.PixelListController,
)

internal data class PixelTextInputTarget(
    val bounds: PixelRect,
    val state: com.purride.pixelui.state.PixelTextFieldState,
    val controller: com.purride.pixelui.state.PixelTextFieldController,
    val readOnly: Boolean,
    val autofocus: Boolean,
    val action: PixelTextInputAction,
    val onChanged: ((String) -> Unit)?,
    val onSubmitted: ((String) -> Unit)?,
)

internal data class PixelRenderResult(
    val buffer: PixelBuffer,
    val clickTargets: List<PixelClickTarget>,
    val pagerTargets: List<PixelPagerTarget>,
    val listTargets: List<PixelListTarget>,
    val textInputTargets: List<PixelTextInputTarget>,
)

internal data class PixelRenderSession(
    val buffer: PixelBuffer,
    val clickTargets: MutableList<PixelClickTarget> = mutableListOf(),
    val pagerTargets: MutableList<PixelPagerTarget> = mutableListOf(),
    val listTargets: MutableList<PixelListTarget> = mutableListOf(),
    val textInputTargets: MutableList<PixelTextInputTarget> = mutableListOf(),
) {
    fun toRenderResult(): PixelRenderResult {
        return PixelRenderResult(
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }
}
