package com.purride.pixellockscreen.ui

/** PIN 键盘中一个可点击按键的固定逻辑几何。 */
internal data class PinKeySpec(
    /** 稳定按键编号；数字键直接使用 `0..9`。 */
    val id: Int,
    /** Pixel Engine 绘制的公开标签。 */
    val label: String,
    /** 按键逻辑左边界。 */
    val left: Int,
    /** 按键逻辑上边界。 */
    val top: Int,
    /** 按键逻辑宽度。 */
    val width: Int,
    /** 按键逻辑高度。 */
    val height: Int,
) {
    /** 判断逻辑坐标是否位于当前按键内。 */
    fun contains(logicalX: Int, logicalY: Int): Boolean =
        logicalX in left until left + width && logicalY in top until top + height
}

/** PIN 页面在 Pixel Engine 固定逻辑视口中的完整布局。 */
internal data class PinCredentialLayout(
    /** 场景逻辑宽度。 */
    val logicalWidth: Int,
    /** 场景逻辑高度。 */
    val logicalHeight: Int,
    /** 主提示左边界。 */
    val promptLeft: Int,
    /** 主提示上边界。 */
    val promptTop: Int,
    /** 主提示宽度。 */
    val promptWidth: Int,
    /** 主提示高度。 */
    val promptHeight: Int,
    /** 反馈文字左边界。 */
    val feedbackLeft: Int,
    /** 反馈文字上边界。 */
    val feedbackTop: Int,
    /** 反馈文字宽度。 */
    val feedbackWidth: Int,
    /** 反馈文字高度。 */
    val feedbackHeight: Int,
    /** PIN 圆点区域左边界。 */
    val indicatorLeft: Int,
    /** PIN 圆点区域上边界。 */
    val indicatorTop: Int,
    /** PIN 圆点区域宽度。 */
    val indicatorWidth: Int,
    /** PIN 圆点区域高度。 */
    val indicatorHeight: Int,
    /** 十二个 PIN 操作按键。 */
    val keys: List<PinKeySpec>,
    /** 紧急入口逻辑左边界。 */
    val emergencyLeft: Int,
    /** 紧急入口逻辑上边界。 */
    val emergencyTop: Int,
    /** 紧急入口逻辑宽度。 */
    val emergencyWidth: Int,
    /** 紧急入口逻辑高度。 */
    val emergencyHeight: Int,
) {
    /** 返回指定逻辑坐标命中的 PIN 按键。 */
    fun keyAt(logicalX: Int, logicalY: Int): PinKeySpec? =
        keys.firstOrNull { key -> key.contains(logicalX, logicalY) }

    /** 判断逻辑坐标是否位于紧急入口。 */
    fun containsEmergency(logicalX: Int, logicalY: Int): Boolean =
        logicalX in emergencyLeft until emergencyLeft + emergencyWidth &&
            logicalY in emergencyTop until emergencyTop + emergencyHeight
}

/** 返回指定逻辑方屏中不会裁切的 PIN 页面布局。 */
internal fun pinCredentialLayout(
    logicalWidth: Int = LOCKSCREEN_LOGICAL_WIDTH,
    logicalHeight: Int = LOCKSCREEN_LOGICAL_HEIGHT,
): PinCredentialLayout {
    require(logicalWidth >= 48 && logicalHeight >= 72) { "pin_logical_viewport_too_small" }
    /** 紧急入口按逻辑高度缩放后的高度。 */
    val emergencyHeight = (logicalHeight / 9).coerceIn(10, 14)
    /** 紧急入口上边界。 */
    val emergencyTop = logicalHeight - emergencyHeight - 2
    /** 数字键盘顶部保持提示、反馈和 PIN 指示器空间。 */
    val keyboardTop = 28
    /** 数字键之间的纵向间距。 */
    val gapY = if (logicalHeight >= 120) 3 else 1
    /** 四行按键在紧急入口上方可用空间内的最大高度。 */
    val keyHeight = ((emergencyTop - 3 - keyboardTop - gapY * 3) / 4).coerceIn(8, 20)
    /** 数字键之间的横向间距。 */
    val gapX = if (logicalWidth >= 120) 4 else 2
    /** 页面左右安全边距。 */
    val horizontalInset = if (logicalWidth >= 120) 11 else 5
    /** 三列按键按当前逻辑宽度均分。 */
    val keyWidth = ((logicalWidth - horizontalInset * 2 - gapX * 2) / 3).coerceAtMost(38)
    /** 键盘整体宽度。 */
    val keyboardWidth = keyWidth * 3 + gapX * 2
    /** 键盘整体横向居中。 */
    val keyboardLeft = (logicalWidth - keyboardWidth) / 2
    /** 紧急入口宽度。 */
    val emergencyWidth = (logicalWidth - 16).coerceAtMost(70)
    return PinCredentialLayout(
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight,
        promptLeft = 4,
        promptTop = 1,
        promptWidth = logicalWidth - 8,
        promptHeight = 9,
        feedbackLeft = 4,
        feedbackTop = 10,
        feedbackWidth = logicalWidth - 8,
        feedbackHeight = 8,
        indicatorLeft = 6,
        indicatorTop = 19,
        indicatorWidth = logicalWidth - 12,
        indicatorHeight = 6,
        keys = buildPinKeys(
            left = keyboardLeft,
            top = keyboardTop,
            width = keyWidth,
            height = keyHeight,
            gapX = gapX,
            gapY = gapY,
        ),
        emergencyLeft = (logicalWidth - emergencyWidth) / 2,
        emergencyTop = emergencyTop,
        emergencyWidth = emergencyWidth,
        emergencyHeight = emergencyHeight,
    )
}

/** 按电话键盘顺序生成 1–9、删除、0、确认四行按键。 */
private fun buildPinKeys(
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    gapX: Int,
    gapY: Int,
): List<PinKeySpec> {
    /** 四行稳定按键编号。 */
    val rows = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
        listOf(PIN_KEY_DELETE, 0, PIN_KEY_CONFIRM),
    )
    return rows.flatMapIndexed { rowIndex, row ->
        row.mapIndexed { columnIndex, keyId ->
            PinKeySpec(
                id = keyId,
                label = when (keyId) {
                    PIN_KEY_DELETE -> "DEL"
                    PIN_KEY_CONFIRM -> "OK"
                    else -> keyId.toString()
                },
                left = left + columnIndex * (width + gapX),
                top = top + rowIndex * (height + gapY),
                width = width,
                height = height,
            )
        }
    }
}

/** 删除键稳定编号。 */
internal const val PIN_KEY_DELETE: Int = 10

/** 确认键稳定编号。 */
internal const val PIN_KEY_CONFIRM: Int = 11
