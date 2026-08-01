package com.purride.pixelshowcase

import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixellockscreen.ui.LockscreenUiState
import com.purride.pixellockscreen.ui.LockscreenBiometricModality
import com.purride.pixellockscreen.ui.LockscreenBiometricPhase
import com.purride.pixellockscreen.ui.LockscreenBiometricUiState
import com.purride.pixellockscreen.ui.PatternCredentialFeedback
import com.purride.pixellockscreen.ui.PatternCredentialUiState
import com.purride.pixellockscreen.ui.PasswordCredentialFeedback
import com.purride.pixellockscreen.ui.PasswordCredentialUiState
import com.purride.pixellockscreen.ui.PinCredentialFeedback
import com.purride.pixellockscreen.ui.PinCredentialUiState

/** 离线预览可切换的真实锁屏宿主场景。 */
internal enum class LockscreenPreviewScene(
    /** 控制面板显示名称。 */
    val label: String,
) {
    /** 普通时钟锁屏。 */
    CLOCK("CLOCK"),

    /** 可交互图案认证。 */
    PATTERN("PATTERN"),

    /** 可交互 PIN 键盘认证。 */
    PIN("PIN"),

    /** 复用系统 IME 语义的密码认证。 */
    PASSWORD("PASSWORD"),
}

/** 离线预览支持的画布方向；它只改变预览框比例，不旋转设备。 */
internal enum class LockscreenPreviewOrientation(
    /** 控制面板显示的紧凑名称。 */
    val label: String,
) {
    PORTRAIT("PORTRAIT"),
    LANDSCAPE("LANDSCAPE"),
}

/** 离线预览用于检查透明叠加可读性的四类固定背景。 */
internal enum class LockscreenPreviewBackground(
    /** 控制面板显示的紧凑名称。 */
    val label: String,
) {
    LIGHT("LIGHT"),
    DARK("DARK"),
    GRAY("GRAY"),
    HIGH_CONTRAST("CONTRAST"),
}

/** 锁屏预览覆盖低、中、高三档稳定电量样本。 */
internal enum class LockscreenPreviewBattery(
    /** 提交给静态宿主的确定电量百分比。 */
    val percent: Int,
) {
    LOW(10),
    MEDIUM(50),
    HIGH(92),
}

/** Showcase 锁屏页的一次完整、可重复离线预览配置。 */
internal data class LockscreenPreviewConfiguration(
    /** 当前预览的真实锁屏场景。 */
    val scene: LockscreenPreviewScene = LockscreenPreviewScene.CLOCK,
    /** 当前主题家族。 */
    val family: ProductThemeFamily = ProductThemeFamily.MIDNIGHT,
    /** 当前实际日夜亮度；离线预览不提供 AUTO。 */
    val brightness: ProductThemeBrightness = ProductThemeBrightness.DARK,
    /** 当前电量样本。 */
    val battery: LockscreenPreviewBattery = LockscreenPreviewBattery.MEDIUM,
    /** 当前是否展示充电状态。 */
    val isCharging: Boolean = false,
    /** 普通锁屏预览使用的生物识别传感器组合。 */
    val biometricModality: LockscreenBiometricModality = LockscreenBiometricModality.NONE,
    /** 普通锁屏预览使用的系统生物识别阶段。 */
    val biometricPhase: LockscreenBiometricPhase = LockscreenBiometricPhase.UNAVAILABLE,
    /** 当前预览框方向。 */
    val orientation: LockscreenPreviewOrientation = LockscreenPreviewOrientation.PORTRAIT,
    /** 当前透明宿主下方的测试背景。 */
    val background: LockscreenPreviewBackground = LockscreenPreviewBackground.HIGH_CONTRAST,
    /** 图案场景使用的可控系统反馈。 */
    val patternFeedback: PatternCredentialFeedback = PatternCredentialFeedback.READY,
    /** PIN 场景使用的可控系统反馈。 */
    val pinFeedback: PinCredentialFeedback = PinCredentialFeedback.READY,
    /** PIN 场景用于检查圆点布局的非敏感输入长度。 */
    val pinInputLength: Int = PREVIEW_PIN_INPUT_LENGTH,
    /** 密码场景使用的可控系统反馈。 */
    val passwordFeedback: PasswordCredentialFeedback = PasswordCredentialFeedback.READY,
    /** 密码场景用于检查掩码布局的非敏感输入长度。 */
    val passwordInputLength: Int = PREVIEW_PASSWORD_INPUT_LENGTH,
    /** 密码场景是否展示原生输入连接已聚焦状态。 */
    val passwordHasInputFocus: Boolean = true,
    /** 密码场景是否展示系统输入法切换入口。 */
    val passwordImeSwitcherVisible: Boolean = true,
) {
    /** 把预览配置转换为固定时间、固定日期的锁屏展示状态。 */
    fun toUiState(): LockscreenUiState = LockscreenUiState(
        timeText = PREVIEW_TIME_TEXT,
        dateText = PREVIEW_DATE_TEXT,
        batteryPercent = battery.percent,
        isCharging = isCharging,
        unlockHint = PREVIEW_UNLOCK_HINT,
        biometric = LockscreenBiometricUiState(
            modality = biometricModality,
            phase = biometricPhase,
            messageText = previewBiometricMessage(biometricPhase),
        ),
    )

    /** 选择传感器组合，并自动修复无传感器与活跃采集阶段的矛盾。 */
    fun withBiometricModality(
        modality: LockscreenBiometricModality,
    ): LockscreenPreviewConfiguration = copy(
        biometricModality = modality,
        biometricPhase = if (
            modality == LockscreenBiometricModality.NONE &&
            biometricPhase != LockscreenBiometricPhase.STRONG_AUTH_REQUIRED
        ) {
            LockscreenBiometricPhase.UNAVAILABLE
        } else {
            biometricPhase
        },
    )

    /** 选择认证阶段，并为需要传感器的阶段自动提供指纹测试样本。 */
    fun withBiometricPhase(
        phase: LockscreenBiometricPhase,
    ): LockscreenPreviewConfiguration = copy(
        biometricPhase = phase,
        biometricModality = if (
            biometricModality == LockscreenBiometricModality.NONE &&
            phase != LockscreenBiometricPhase.UNAVAILABLE &&
            phase != LockscreenBiometricPhase.STRONG_AUTH_REQUIRED
        ) {
            LockscreenBiometricModality.FINGERPRINT
        } else {
            biometricModality
        },
    )

    /** 把可控反馈转换为不包含路径的图案认证状态。 */
    fun toPatternUiState(): PatternCredentialUiState = PatternCredentialUiState(
        promptText = PREVIEW_PATTERN_PROMPT,
        feedbackText = when (patternFeedback) {
            PatternCredentialFeedback.READY -> ""
            PatternCredentialFeedback.CHECKING -> "CHECKING"
            PatternCredentialFeedback.ERROR -> "TRY AGAIN"
            PatternCredentialFeedback.LOCKED_OUT -> "WAIT 30S"
        },
        feedback = patternFeedback,
    )

    /** 把可控反馈转换为只包含输入长度的 PIN 认证状态。 */
    fun toPinUiState(): PinCredentialUiState = PinCredentialUiState(
        promptText = PREVIEW_PIN_PROMPT,
        inputLength = pinInputLength,
        feedbackText = when (pinFeedback) {
            PinCredentialFeedback.READY -> ""
            PinCredentialFeedback.CHECKING -> "CHECKING"
            PinCredentialFeedback.ERROR -> "TRY AGAIN"
            PinCredentialFeedback.LOCKED_OUT -> "WAIT 30S"
        },
        feedback = pinFeedback,
    )

    /** 把可控反馈转换为只包含长度、焦点和入口状态的密码认证状态。 */
    fun toPasswordUiState(): PasswordCredentialUiState = PasswordCredentialUiState(
        promptText = PREVIEW_PASSWORD_PROMPT,
        inputLength = passwordInputLength,
        feedbackText = when (passwordFeedback) {
            PasswordCredentialFeedback.READY -> ""
            PasswordCredentialFeedback.CHECKING -> "CHECKING"
            PasswordCredentialFeedback.ERROR -> "TRY AGAIN"
            PasswordCredentialFeedback.LOCKED_OUT -> "WAIT 30S"
        },
        feedback = passwordFeedback,
        hasInputFocus = passwordHasInputFocus,
        isImeSwitcherVisible = passwordImeSwitcherVisible,
    )
}

/** 为离线截图提供稳定且不依赖设备语言的生物识别消息。 */
private fun previewBiometricMessage(phase: LockscreenBiometricPhase): String = when (phase) {
    LockscreenBiometricPhase.UNAVAILABLE -> ""
    LockscreenBiometricPhase.READY -> "TOUCH SENSOR"
    LockscreenBiometricPhase.SCANNING -> "SCANNING"
    LockscreenBiometricPhase.SUCCESS -> "UNLOCKED"
    LockscreenBiometricPhase.ERROR -> "NOT RECOGNIZED"
    LockscreenBiometricPhase.LOCKED_OUT -> "TOO MANY ATTEMPTS"
    LockscreenBiometricPhase.STRONG_AUTH_REQUIRED -> "USE PIN AFTER RESTART"
}

/** 按枚举声明顺序循环选择前一个或后一个主题家族。 */
internal fun cyclePreviewFamily(current: ProductThemeFamily, step: Int): ProductThemeFamily {
    /** 全部主题的稳定声明顺序。 */
    val families = ProductThemeFamily.entries
    /** 使用 floorMod 保证向前循环时不会产生负下标。 */
    val nextIndex = Math.floorMod(current.ordinal + step, families.size)
    return families[nextIndex]
}

/** 按枚举声明顺序循环选择前一个或后一个测试背景。 */
internal fun cyclePreviewBackground(
    current: LockscreenPreviewBackground,
    step: Int,
): LockscreenPreviewBackground {
    /** 全部背景的稳定声明顺序。 */
    val backgrounds = LockscreenPreviewBackground.entries
    /** 使用 floorMod 保证向前循环时不会产生负下标。 */
    val nextIndex = Math.floorMod(current.ordinal + step, backgrounds.size)
    return backgrounds[nextIndex]
}

/** 截图回归使用的固定时间。 */
private const val PREVIEW_TIME_TEXT = "09:41"

/** 截图回归使用的固定长日期。 */
private const val PREVIEW_DATE_TEXT = "SATURDAY, AUGUST 1"

/** 首版静态锁屏使用的固定解锁提示。 */
private const val PREVIEW_UNLOCK_HINT = "SWIPE UP TO UNLOCK"

/** 图案预览使用的固定主提示。 */
private const val PREVIEW_PATTERN_PROMPT = "DRAW PATTERN"

/** PIN 预览使用的固定主提示。 */
private const val PREVIEW_PIN_PROMPT = "ENTER PIN"

/** PIN 预览默认展示的非敏感输入圆点数量。 */
private const val PREVIEW_PIN_INPUT_LENGTH = 4

/** 密码预览使用的固定主提示。 */
private const val PREVIEW_PASSWORD_PROMPT = "ENTER PASSWORD"

/** 密码预览默认展示的非敏感输入圆点数量。 */
private const val PREVIEW_PASSWORD_INPUT_LENGTH = 8
