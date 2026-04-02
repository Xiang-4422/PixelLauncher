package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelClickableElement
import com.purride.pixelui.internal.legacy.PixelFillMaxHeightElement
import com.purride.pixelui.internal.legacy.PixelFillMaxWidthElement
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelPaddingElement
import com.purride.pixelui.internal.legacy.PixelSizeElement

internal data class PixelModifierInfo(
    val paddingLeft: Int = 0,
    val paddingTop: Int = 0,
    val paddingRight: Int = 0,
    val paddingBottom: Int = 0,
    val fixedWidth: Int? = null,
    val fixedHeight: Int? = null,
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

internal object PixelModifierSupport {
    fun resolve(modifier: PixelModifier): PixelModifierInfo {
        var paddingLeft = 0
        var paddingTop = 0
        var paddingRight = 0
        var paddingBottom = 0
        var fixedWidth: Int? = null
        var fixedHeight: Int? = null
        var fillMaxWidth = false
        var fillMaxHeight = false
        var onClick: (() -> Unit)? = null

        modifier.elements.forEach { element ->
            when (element) {
                is PixelPaddingElement -> {
                    paddingLeft += element.left
                    paddingTop += element.top
                    paddingRight += element.right
                    paddingBottom += element.bottom
                }

                is PixelSizeElement -> {
                    fixedWidth = element.width ?: fixedWidth
                    fixedHeight = element.height ?: fixedHeight
                }

                is PixelFillMaxWidthElement -> fillMaxWidth = element.enabled
                is PixelFillMaxHeightElement -> fillMaxHeight = element.enabled
                is PixelClickableElement -> onClick = element.onClick
                else -> Unit
            }
        }

        return PixelModifierInfo(
            paddingLeft = paddingLeft,
            paddingTop = paddingTop,
            paddingRight = paddingRight,
            paddingBottom = paddingBottom,
            fixedWidth = fixedWidth,
            fixedHeight = fixedHeight,
            fillMaxWidth = fillMaxWidth,
            fillMaxHeight = fillMaxHeight,
            onClick = onClick,
        )
    }
}
