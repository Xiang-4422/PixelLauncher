package com.purride.pixelui

import com.purride.pixelcore.PixelTone

/**
 * 像素按钮样式。
 *
 * 第一版先稳定按钮最核心的三层表现：
 * 1. 外层填充色
 * 2. 边框色
 * 3. 按钮文本样式
 *
 * 按钮尺寸、点击范围和布局仍由调用方通过 `modifier` 控制，
 * 这样可以保持基础组件足够轻，同时满足 demo 和后续页面组合需要。
 */
data class PixelButtonStyle(
    val fillTone: PixelTone = PixelTone.OFF,
    val borderTone: PixelTone? = PixelTone.ON,
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
    val alignment: PixelAlignment = PixelAlignment.CENTER,
) {
    companion object {
        val Default = PixelButtonStyle()
        val Accent = PixelButtonStyle(
            fillTone = PixelTone.OFF,
            borderTone = PixelTone.ACCENT,
            textStyle = PixelTextStyle.Accent,
        )
        val Disabled = PixelButtonStyle(
            fillTone = PixelTone.OFF,
            borderTone = PixelTone.ON,
            textStyle = PixelTextStyle(tone = PixelTone.OFF),
        )
    }
}

/**
 * 最小可用像素按钮组件。
 *
 * 当前实现仍然由 `PixelSurface + PixelBox + PixelText` 组合而成，
 * 但页面层不需要再重复手写这套按钮结构，后续如果按钮有统一交互或视觉演进，
 * 也可以只在这里收敛修改。
 */
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: PixelModifier = PixelModifier.Empty,
    style: PixelButtonStyle = PixelButtonStyle.Default,
    enabled: Boolean = true,
    key: Any? = null,
): PixelNode {
    val resolvedStyle = if (enabled) style else PixelButtonStyle.Disabled
    return PixelSurface(
        modifier = if (enabled) modifier.clickable(onClick) else modifier,
        fillTone = resolvedStyle.fillTone,
        borderTone = resolvedStyle.borderTone,
        child = PixelBox(
            modifier = PixelModifier.Empty.fillMaxSize(),
            alignment = resolvedStyle.alignment,
            children = listOf(
                PixelText(
                    text = text,
                    style = resolvedStyle.textStyle,
                ),
            ),
        ),
        key = key,
    )
}
