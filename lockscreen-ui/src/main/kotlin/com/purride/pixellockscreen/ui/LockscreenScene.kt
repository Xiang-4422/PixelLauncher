package com.purride.pixellockscreen.ui

import com.purride.pixelcore.PixelColor
import com.purride.pixeldesign.ProductPalette
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeCatalog
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixelui.Alignment
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Positioned
import com.purride.pixelui.Row
import com.purride.pixelui.SafeArea
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset

/** 纵向锁屏使用的固定逻辑宽度。 */
internal const val LOCKSCREEN_PORTRAIT_WIDTH = 96

/** 纵向锁屏使用的固定逻辑高度。 */
internal const val LOCKSCREEN_PORTRAIT_HEIGHT = 192

/** 横向锁屏使用的固定逻辑宽度。 */
internal const val LOCKSCREEN_LANDSCAPE_WIDTH = 192

/** 横向锁屏使用的固定逻辑高度。 */
internal const val LOCKSCREEN_LANDSCAPE_HEIGHT = 96

/** 锁屏内容和安全区之间保留的最小逻辑像素留白。 */
private const val LOCKSCREEN_CONTENT_INSET = 6

/** 锁屏中央信息组内部的逻辑像素间距。 */
private const val LOCKSCREEN_INFO_SPACING = 4

/** 普通锁屏直接绘制的通知卡数量，额外条目合并为计数提示。 */
private const val MAXIMUM_RENDERED_NOTIFICATIONS = 2

/** 透明锁屏静态场景所需的全部不可变输入。 */
internal data class LockscreenSceneRequest(
    /** 当前格式化后的展示状态。 */
    val state: LockscreenUiState,
    /** 当前主题家族。 */
    val family: ProductThemeFamily,
    /** 当前主题实际亮度。 */
    val brightness: ProductThemeBrightness,
    /** 当前宿主是否采用横向布局。 */
    val isLandscape: Boolean,
    /** 可选的原生通知和媒体操作转发器。 */
    val contentListener: LockscreenContentListener? = null,
)

/** 判断下一帧是否包含需要重新提交给像素引擎的实际变化。 */
internal fun shouldSubmitLockscreenRequest(
    previous: LockscreenSceneRequest?,
    next: LockscreenSceneRequest,
): Boolean = previous != next

/** 根据不可变请求构建不含系统服务、手势或连续动画的锁屏 Widget 树。 */
internal fun buildLockscreenScene(request: LockscreenSceneRequest): Widget {
    /** 当前请求解析出的共享产品色板。 */
    val palette = ProductThemeCatalog.resolve(request.family, request.brightness)
    /** 横屏降低时间倍率，避免较短安全高度裁切其他状态。 */
    val timeScale = lockscreenTimeScale(request.isLandscape)
    /** 横屏将辅助信息保持为单倍字体，纵屏日期使用双倍字体强化层级。 */
    val dateScale = if (request.isLandscape) 1 else 2
    /** 交互锁屏或 AOD 对应的完整场景内容。 */
    val sceneContent = if (request.state.ambient.isAmbient) {
        ambientLockscreenContent(request.state, palette, timeScale, dateScale)
    } else {
        interactiveLockscreenContent(
            request.state,
            palette,
            timeScale,
            dateScale,
            request.contentListener,
        )
    }
    return SafeArea(
        minimum = com.purride.pixelui.PixelWindowInsets(
            left = LOCKSCREEN_CONTENT_INSET,
            top = LOCKSCREEN_CONTENT_INSET,
            right = LOCKSCREEN_CONTENT_INSET,
            bottom = LOCKSCREEN_CONTENT_INSET,
        ),
        child = Transform.translate(
            offset = IntOffset(
                x = request.state.ambient.burnInOffsetX,
                y = request.state.ambient.burnInOffsetY,
            ),
            child = sceneContent,
            key = "lockscreen-ambient-offset",
        ),
    )
}

/** 绘制可交互锁屏中的时间、安全状态、内容摘要和解锁提示。 */
private fun interactiveLockscreenContent(
    state: LockscreenUiState,
    palette: ProductPalette,
    timeScale: Int,
    dateScale: Int,
    contentListener: LockscreenContentListener?,
): Widget = Column(
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            children = listOf(
                Expanded(
                    child = Container(
                        alignment = Alignment.CENTER,
                        child = Column(
                            mainAxisSize = MainAxisSize.MIN,
                            mainAxisAlignment = MainAxisAlignment.CENTER,
                            crossAxisAlignment = CrossAxisAlignment.CENTER,
                            spacing = LOCKSCREEN_INFO_SPACING,
                            children = listOf(
                                outlinedLockscreenText(
                                    text = state.timeText,
                                    foreground = palette.primary,
                                    backing = palette.background,
                                    fontScale = timeScale,
                                    key = "lockscreen-time",
                                ),
                                outlinedLockscreenText(
                                    text = state.dateText,
                                    foreground = palette.secondary,
                                    backing = palette.background,
                                    fontScale = dateScale,
                                    key = "lockscreen-date",
                                ),
                                batteryStatus(state, palette),
                                visibleSecurityStatus(
                                    biometric = state.biometric,
                                    notice = state.securityNotice,
                                    palette = palette,
                                ),
                            ),
                        ),
                    ),
                ),
                lockscreenContentCards(state, palette, contentListener),
                outlinedLockscreenText(
                    text = state.unlockHint,
                    foreground = palette.muted,
                    backing = palette.background,
                    fontScale = 1,
                    key = "lockscreen-unlock-hint",
                ),
            ),
            key = "lockscreen-interactive-content",
        )

/** AOD 只绘制时间、日期和电量，不暴露通知、安全提示或解锁操作。 */
private fun ambientLockscreenContent(
    state: LockscreenUiState,
    palette: ProductPalette,
    timeScale: Int,
    dateScale: Int,
): Widget = Column(
    mainAxisSize = MainAxisSize.MAX,
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    children = listOf(
        Expanded(
            child = Container(
                alignment = Alignment.CENTER,
                child = Column(
                    mainAxisSize = MainAxisSize.MIN,
                    mainAxisAlignment = MainAxisAlignment.CENTER,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    spacing = LOCKSCREEN_INFO_SPACING,
                    children = listOf(
                        outlinedLockscreenText(
                            text = state.timeText,
                            foreground = palette.primary,
                            backing = palette.background,
                            fontScale = timeScale,
                            key = "lockscreen-ambient-time",
                        ),
                        outlinedLockscreenText(
                            text = state.dateText,
                            foreground = palette.secondary,
                            backing = palette.background,
                            fontScale = dateScale,
                            key = "lockscreen-ambient-date",
                        ),
                        batteryStatus(state, palette),
                    ),
                    key = "lockscreen-ambient-info",
                ),
            ),
        ),
    ),
    key = "lockscreen-ambient-content",
)

/** 只在存在媒体或通知摘要时绘制底部紧凑内容区。 */
private fun lockscreenContentCards(
    state: LockscreenUiState,
    palette: ProductPalette,
    contentListener: LockscreenContentListener?,
): Widget {
    /** 当前需要展示的媒体卡和通知卡。 */
    val cards = buildList {
        if (state.media.isVisible) {
            add(interactiveMediaCard(state.media, palette, contentListener))
        }
        state.notifications.take(MAXIMUM_RENDERED_NOTIFICATIONS).forEach { notification ->
            add(interactiveNotificationCard(notification, palette, contentListener))
        }
        if (state.notifications.size > MAXIMUM_RENDERED_NOTIFICATIONS) {
            add(
                outlinedLockscreenText(
                    text = "+${state.notifications.size - MAXIMUM_RENDERED_NOTIFICATIONS} MORE",
                    foreground = palette.muted,
                    backing = palette.background,
                    fontScale = 1,
                    key = "lockscreen-notification-more",
                ),
            )
        }
    }
    if (cards.isEmpty()) {
        return SizedBox(width = 0, height = 0, key = "lockscreen-content-hidden")
    }
    return Padding(
        padding = EdgeInsets.symmetric(horizontal = 5, vertical = 2),
        child = Column(
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
            spacing = 2,
            children = cards,
            key = "lockscreen-content-cards",
        ),
    )
}

/** 仅在运行时监听器存在时把媒体卡变为播放/暂停点击目标。 */
private fun interactiveMediaCard(
    state: LockscreenMediaUiState,
    palette: ProductPalette,
    contentListener: LockscreenContentListener?,
): Widget {
    /** 当前媒体卡的纯视觉内容。 */
    val card = mediaCard(state, palette)
    return if (contentListener == null) {
        card
    } else {
        GestureDetector(
            child = card,
            onTap = {
                runCatching(contentListener::onMediaPlayPauseRequested)
                    .onFailure(contentListener::onInteractionFailure)
            },
            key = "lockscreen-media-action",
        )
    }
}

/** 仅在运行时监听器存在时把通知卡映射到对应脱敏键。 */
private fun interactiveNotificationCard(
    state: LockscreenNotificationUiState,
    palette: ProductPalette,
    contentListener: LockscreenContentListener?,
): Widget {
    /** 当前通知卡的纯视觉内容。 */
    val card = notificationCard(state, palette)
    return if (contentListener == null) {
        card
    } else {
        GestureDetector(
            child = card,
            onTap = {
                runCatching { contentListener.onNotificationRequested(state.key) }
                    .onFailure(contentListener::onInteractionFailure)
            },
            key = "lockscreen-notification-action-${state.key}",
        )
    }
}

/** 绘制一条经过 SystemUI 隐私裁剪的紧凑通知卡。 */
private fun notificationCard(
    state: LockscreenNotificationUiState,
    palette: ProductPalette,
): Widget {
    /** 隐私替代通知不使用正文标题。 */
    val title = when {
        state.isRedacted -> "CONTENT HIDDEN"
        state.titleText.isNotBlank() -> state.titleText
        else -> "NEW NOTIFICATION"
    }
    return compactContentCard(
        primaryText = state.appText,
        secondaryText = title,
        color = if (state.isRedacted) palette.muted else palette.primary,
        backing = palette.background,
        glyph = ContentGlyph.NOTIFICATION,
        key = "lockscreen-notification-${state.key}",
    )
}

/** 绘制 SystemUI 当前选中媒体会话的只读摘要卡。 */
private fun mediaCard(state: LockscreenMediaUiState, palette: ProductPalette): Widget =
    compactContentCard(
        primaryText = state.titleText,
        secondaryText = state.artistText.ifBlank { if (state.isPlaying) "PLAYING" else "PAUSED" },
        color = palette.secondary,
        backing = palette.background,
        glyph = if (state.isPlaying) ContentGlyph.PAUSE else ContentGlyph.PLAY,
        key = "lockscreen-media",
    )

/** 内容卡使用的三种稳定像素图形。 */
private enum class ContentGlyph {
    /** 通知铃铛。 */
    NOTIFICATION,

    /** 媒体播放三角。 */
    PLAY,

    /** 媒体暂停双竖线。 */
    PAUSE,
}

/** 绘制固定 86×16 的双行像素内容卡。 */
private fun compactContentCard(
    primaryText: String,
    secondaryText: String,
    color: PixelColor,
    backing: PixelColor,
    glyph: ContentGlyph,
    key: String,
): Widget = Stack(
    children = listOf(
        CustomPaint(width = 86, height = 16, key = "$key-border") {
            drawRect(left = 0, top = 0, width = 86, height = 16, color = color)
            when (glyph) {
                ContentGlyph.NOTIFICATION -> {
                    drawRect(left = 4, top = 4, width = 7, height = 6, color = color)
                    fillRect(left = 6, top = 2, width = 3, height = 2, color = color)
                    fillRect(left = 6, top = 11, width = 3, height = 1, color = color)
                }
                ContentGlyph.PLAY -> {
                    fillRect(left = 5, top = 4, width = 2, height = 8, color = color)
                    fillRect(left = 7, top = 5, width = 2, height = 6, color = color)
                    fillRect(left = 9, top = 7, width = 2, height = 2, color = color)
                }
                ContentGlyph.PAUSE -> {
                    fillRect(left = 5, top = 4, width = 2, height = 8, color = color)
                    fillRect(left = 9, top = 4, width = 2, height = 8, color = color)
                }
            }
        },
        Positioned(
            left = 15,
            top = 1,
            width = 69,
            height = 14,
            child = Column(
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    outlinedLockscreenText(primaryText, color, backing, 1, "$key-primary"),
                    outlinedLockscreenText(secondaryText, color, backing, 1, "$key-secondary"),
                ),
            ),
        ),
    ),
    key = key,
)

/** 信任提示存在时优先于生物识别阶段，确保单个安全区域只表达一个系统决策。 */
private fun visibleSecurityStatus(
    biometric: LockscreenBiometricUiState,
    notice: LockscreenSecurityNoticeUiState,
    palette: ProductPalette,
): Widget = if (notice.isVisible) {
    securityNoticeStatus(notice, palette)
} else {
    biometricStatus(biometric, palette)
}

/** 绘制信任代理或 Extend Unlock 的单行像素安全提示。 */
private fun securityNoticeStatus(
    state: LockscreenSecurityNoticeUiState,
    palette: ProductPalette,
): Widget {
    /** 信任成功、错误和持续解锁分别使用的可读主题颜色。 */
    val color = when (state.phase) {
        LockscreenSecurityNoticePhase.TRUSTED -> palette.primary
        LockscreenSecurityNoticePhase.TRUST_ERROR -> palette.alert
        LockscreenSecurityNoticePhase.EXTENDED_UNLOCK -> palette.secondary
        LockscreenSecurityNoticePhase.NONE -> palette.muted
    }
    return Row(
        mainAxisSize = MainAxisSize.MIN,
        mainAxisAlignment = MainAxisAlignment.CENTER,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        spacing = 3,
        children = listOf(
            securityNoticeGlyph(state.phase, color),
            outlinedLockscreenText(
                text = state.messageText,
                foreground = color,
                backing = palette.background,
                fontScale = 1,
                key = "lockscreen-security-notice-text",
            ),
        ),
        key = "lockscreen-security-notice",
    )
}

/** 为信任成功、错误和持续解锁绘制稳定的 9×9 像素图形。 */
private fun securityNoticeGlyph(
    phase: LockscreenSecurityNoticePhase,
    color: PixelColor,
): Widget = CustomPaint(width = 9, height = 9, key = "lockscreen-security-notice-glyph") {
    when (phase) {
        LockscreenSecurityNoticePhase.TRUSTED -> {
            drawRect(left = 2, top = 4, width = 5, height = 4, color = color)
            fillRect(left = 5, top = 2, width = 2, height = 2, color = color)
            fillRect(left = 6, top = 1, width = 2, height = 1, color = color)
        }
        LockscreenSecurityNoticePhase.TRUST_ERROR -> {
            fillRect(left = 1, top = 1, width = 1, height = 1, color = color)
            fillRect(left = 7, top = 1, width = 1, height = 1, color = color)
            fillRect(left = 2, top = 2, width = 1, height = 1, color = color)
            fillRect(left = 6, top = 2, width = 1, height = 1, color = color)
            fillRect(left = 3, top = 3, width = 3, height = 3, color = color)
            fillRect(left = 2, top = 6, width = 1, height = 1, color = color)
            fillRect(left = 6, top = 6, width = 1, height = 1, color = color)
            fillRect(left = 1, top = 7, width = 1, height = 1, color = color)
            fillRect(left = 7, top = 7, width = 1, height = 1, color = color)
        }
        LockscreenSecurityNoticePhase.EXTENDED_UNLOCK -> {
            drawRect(left = 1, top = 1, width = 7, height = 7, color = color)
            fillRect(left = 4, top = 2, width = 1, height = 3, color = color)
            fillRect(left = 4, top = 4, width = 3, height = 1, color = color)
        }
        LockscreenSecurityNoticePhase.NONE -> Unit
    }
}

/** 在普通锁屏中绘制系统生物识别阶段；不可用状态保持零尺寸。 */
private fun biometricStatus(state: LockscreenBiometricUiState, palette: ProductPalette): Widget {
    if (!state.isVisible) {
        return SizedBox(width = 0, height = 0, key = "lockscreen-biometric-hidden")
    }
    /** 当前阶段使用的可读主题颜色。 */
    val color = when (state.phase) {
        LockscreenBiometricPhase.SUCCESS -> palette.primary
        LockscreenBiometricPhase.ERROR,
        LockscreenBiometricPhase.LOCKED_OUT,
        -> palette.alert
        LockscreenBiometricPhase.STRONG_AUTH_REQUIRED -> palette.secondary
        LockscreenBiometricPhase.READY,
        LockscreenBiometricPhase.SCANNING,
        -> palette.outline
        LockscreenBiometricPhase.UNAVAILABLE -> palette.muted
    }
    /** 系统未提供临时消息时使用的稳定阶段说明。 */
    val label = state.messageText.ifBlank {
        when (state.phase) {
            LockscreenBiometricPhase.READY -> "BIOMETRIC READY"
            LockscreenBiometricPhase.SCANNING -> "SCANNING"
            LockscreenBiometricPhase.SUCCESS -> "UNLOCKED"
            LockscreenBiometricPhase.ERROR -> "TRY AGAIN"
            LockscreenBiometricPhase.LOCKED_OUT -> "BIOMETRIC LOCKED"
            LockscreenBiometricPhase.STRONG_AUTH_REQUIRED -> "USE PASSWORD"
            LockscreenBiometricPhase.UNAVAILABLE -> ""
        }
    }
    return Row(
        mainAxisSize = MainAxisSize.MIN,
        mainAxisAlignment = MainAxisAlignment.CENTER,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        spacing = 3,
        children = listOf(
            biometricGlyph(state.modality, state.phase, color),
            outlinedLockscreenText(
                text = label,
                foreground = color,
                backing = palette.background,
                fontScale = 1,
                key = "lockscreen-biometric-text",
            ),
        ),
        key = "lockscreen-biometric-status",
    )
}

/** 按传感器种类和 StrongAuth 状态绘制紧凑的像素安全图形。 */
private fun biometricGlyph(
    modality: LockscreenBiometricModality,
    phase: LockscreenBiometricPhase,
    color: PixelColor,
): Widget = CustomPaint(width = 9, height = 9, key = "lockscreen-biometric-glyph") {
    if (phase == LockscreenBiometricPhase.STRONG_AUTH_REQUIRED) {
        drawRect(left = 2, top = 4, width = 5, height = 4, color = color)
        fillRect(left = 3, top = 2, width = 3, height = 2, color = color)
        return@CustomPaint
    }
    when (modality) {
        LockscreenBiometricModality.FACE -> {
            drawRect(left = 1, top = 1, width = 7, height = 7, color = color)
            fillRect(left = 3, top = 3, width = 1, height = 1, color = color)
            fillRect(left = 5, top = 3, width = 1, height = 1, color = color)
            fillRect(left = 3, top = 6, width = 3, height = 1, color = color)
        }
        LockscreenBiometricModality.FINGERPRINT,
        LockscreenBiometricModality.FACE_AND_FINGERPRINT,
        -> {
            fillRect(left = 3, top = 1, width = 3, height = 1, color = color)
            fillRect(left = 1, top = 3, width = 1, height = 3, color = color)
            fillRect(left = 7, top = 3, width = 1, height = 3, color = color)
            drawRect(left = 3, top = 3, width = 3, height = 5, color = color)
            fillRect(left = 4, top = 5, width = 1, height = 4, color = color)
        }
        LockscreenBiometricModality.NONE -> {
            drawRect(left = 2, top = 2, width = 5, height = 5, color = color)
        }
    }
}

/** 返回当前方向下的大时钟整数像素倍率。 */
internal fun lockscreenTimeScale(isLandscape: Boolean): Int = if (isLandscape) 3 else 4

/** 组合私有电池图形和百分比文字，不向通用图标目录引入锁屏专用状态。 */
private fun batteryStatus(state: LockscreenUiState, palette: ProductPalette): Widget {
    /** 充电状态使用警示色加强识别，普通状态沿用主题主色。 */
    val statusColor = if (state.isCharging) palette.alert else palette.primary
    /** 电量百分比和充电状态组成的稳定只读文本。 */
    val statusText = buildString {
        append(state.batteryPercent)
        append('%')
        if (state.isCharging) append(" CHARGING")
    }
    return Row(
        mainAxisSize = MainAxisSize.MIN,
        mainAxisAlignment = MainAxisAlignment.CENTER,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        spacing = 3,
        children = listOf(
            batteryGlyph(state.batteryPercent, statusColor, palette.background),
            outlinedLockscreenText(
                text = statusText,
                foreground = statusColor,
                backing = palette.background,
                fontScale = 1,
                key = "lockscreen-battery-text",
            ),
        ),
    )
}

/** 绘制一个包含真实电量填充的紧凑像素电池图形。 */
private fun batteryGlyph(level: Int, color: PixelColor, backing: PixelColor): Widget {
    /** 九像素内部宽度按百分比向上取整，1% 仍保留一个可见像素。 */
    val filledWidth = if (level == 0) 0 else ((level * 9) + 99) / 100
    return CustomPaint(width = 13, height = 7, key = "lockscreen-battery-glyph") {
        drawRect(left = 0, top = 1, width = 11, height = 5, color = backing)
        drawRect(left = 0, top = 1, width = 11, height = 5, color = color)
        fillRect(left = 11, top = 2, width = 2, height = 3, color = color)
        if (filledWidth > 0) {
            fillRect(left = 1, top = 2, width = filledWidth, height = 3, color = color)
        }
    }
}

/** 通过四向单像素底色副本给透明壁纸上的文本提供硬边反差。 */
internal fun outlinedLockscreenText(
    text: String,
    foreground: PixelColor,
    backing: PixelColor,
    fontScale: Int,
    key: Any,
): Widget {
    /** 主文字和描边文字共享的整数倍率与单行约束。 */
    fun textLayer(color: PixelColor, layerKey: String): Widget = Text(
        data = text,
        style = TextStyle(color = color, fontScale = fontScale),
        softWrap = false,
        maxLines = 1,
        overflow = TextOverflow.ELLIPSIS,
        textAlign = TextAlign.CENTER,
        key = "$key-$layerKey",
    )
    /** 描边副本相对主文字采用的四个正交偏移。 */
    val outlineOffsets = listOf(
        IntOffset(-1, 0),
        IntOffset(1, 0),
        IntOffset(0, -1),
        IntOffset(0, 1),
    )
    return Padding(
        padding = EdgeInsets.all(1),
        child = Stack(
            alignment = Alignment.CENTER,
            children = outlineOffsets.mapIndexed { index, offset ->
                Transform.translate(
                    offset = offset,
                    child = textLayer(backing, "outline-$index"),
                    key = "$key-outline-transform-$index",
                )
            } + textLayer(foreground, "foreground"),
            key = "$key-stack",
        ),
        key = "$key-padding",
    )
}
