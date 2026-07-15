package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/** 定义 `PixelColorRole` 在 `PixelColorScheme` 中承担的数据与行为边界。
 *
 * Semantic color roles referenced by component tokens instead of copied concrete colors.
 */
public enum class PixelColorRole {
    /** Root canvas background. */
    Background,

    /** Content drawn on the root canvas background. */
    OnBackground,

    /** Standard component or overlay surface. */
    Surface,

    /** Content drawn on a standard surface. */
    OnSurface,

    /** Secondary surface used for interaction feedback. */
    SurfaceVariant,

    /** Content drawn on a secondary surface. */
    OnSurfaceVariant,

    /** Primary component outline. */
    Outline,

    /** Lower-emphasis component outline. */
    OutlineVariant,

    /** Primary accent and selected content. */
    Primary,

    /** Content drawn on the primary accent. */
    OnPrimary,

    /** Error or destructive surface. */
    Danger,

    /** Content drawn on an error or destructive surface. */
    OnDanger,

    /** Warning surface or content. */
    Warning,

    /** Content drawn on a warning surface. */
    OnWarning,

    /** Disabled component surface or outline. */
    Disabled,

    /** Disabled component content. */
    OnDisabled,

    /** Inactive but still enabled content. */
    Inactive,

    /** Track behind slider, switch, progress, or scroll indicators. */
    Track,

    /** Keyboard and accessibility focus indicator. */
    Focus,

    /** Selection, caret, and composition indicator. */
    Selection,

    /** Modal overlay scrim. */
    Scrim,

    /** Hard-edged pixel elevation shadow. */
    Shadow,
}

/**
 * 定义 `PixelColorScheme` 在 `PixelColorScheme` 中承担的数据与行为边界。
 *
 * Complete semantic color scheme for standard Pixel UI components.
 *
 * Component tokens retain [PixelColorRole] values and call [resolve] at build time, so copying a
 * scheme with changed concrete colors propagates without rebuilding component token objects.
 *
 * @property background Root canvas background.
 * @property onBackground Default content drawn on [background].
 * @property surface Standard component and overlay surface.
 * @property onSurface Default content drawn on [surface].
 * @property surfaceVariant Secondary surface used for interaction feedback.
 * @property onSurfaceVariant Content drawn on [surfaceVariant].
 * @property outline Primary component outline.
 * @property outlineVariant Lower-emphasis outline.
 * @property primary Primary accent and selected content.
 * @property onPrimary Content drawn on [primary].
 * @property danger Error or destructive surface.
 * @property onDanger Content drawn on [danger].
 * @property warning Warning surface or content.
 * @property onWarning Content drawn on [warning].
 * @property disabled Disabled component surface or outline.
 * @property onDisabled Disabled component content.
 * @property inactive Inactive but enabled content.
 * @property track Track behind range and progress indicators.
 * @property focus Keyboard and accessibility focus indicator.
 * @property selection Selection, caret, and composition indicator.
 * @property scrim Modal overlay scrim.
 * @property shadow Hard-edged elevation shadow.
 */
public data class PixelColorScheme(
    public val background: PixelColor,
    public val onBackground: PixelColor,
    public val surface: PixelColor,
    public val onSurface: PixelColor,
    public val surfaceVariant: PixelColor,
    public val onSurfaceVariant: PixelColor,
    public val outline: PixelColor,
    public val outlineVariant: PixelColor,
    public val primary: PixelColor,
    public val onPrimary: PixelColor,
    public val danger: PixelColor,
    public val onDanger: PixelColor,
    public val warning: PixelColor,
    public val onWarning: PixelColor,
    public val disabled: PixelColor,
    public val onDisabled: PixelColor,
    public val inactive: PixelColor,
    public val track: PixelColor,
    public val focus: PixelColor,
    public val selection: PixelColor,
    public val scrim: PixelColor,
    public val shadow: PixelColor,
) {
    /** 查询 `PixelColorScheme` 的 `resolve` 结果，不产生额外状态变更。
 *
 * Resolves [role] to the concrete color in this scheme.
 */
    public fun resolve(role: PixelColorRole): PixelColor {
        return when (role) {
            PixelColorRole.Background -> background
            PixelColorRole.OnBackground -> onBackground
            PixelColorRole.Surface -> surface
            PixelColorRole.OnSurface -> onSurface
            PixelColorRole.SurfaceVariant -> surfaceVariant
            PixelColorRole.OnSurfaceVariant -> onSurfaceVariant
            PixelColorRole.Outline -> outline
            PixelColorRole.OutlineVariant -> outlineVariant
            PixelColorRole.Primary -> primary
            PixelColorRole.OnPrimary -> onPrimary
            PixelColorRole.Danger -> danger
            PixelColorRole.OnDanger -> onDanger
            PixelColorRole.Warning -> warning
            PixelColorRole.OnWarning -> onWarning
            PixelColorRole.Disabled -> disabled
            PixelColorRole.OnDisabled -> onDisabled
            PixelColorRole.Inactive -> inactive
            PixelColorRole.Track -> track
            PixelColorRole.Focus -> focus
            PixelColorRole.Selection -> selection
            PixelColorRole.Scrim -> scrim
            PixelColorRole.Shadow -> shadow
        }
    }

    /** 执行 `PixelColorScheme` 的 `toLegacyColors` 公开行为；具体参数、返回和副作用见下文。
 *
 * Converts this scheme to the legacy thirteen-color palette.
 */
    public fun toLegacyColors(): PixelThemeColors {
        return PixelThemeColors(
            background = background,
            surface = surface,
            text = onBackground,
            mutedText = onSurfaceVariant,
            border = outline,
            accent = primary,
            danger = danger,
            warning = warning,
            disabled = disabled,
            inactive = inactive,
            track = track,
            focus = focus,
            selection = selection,
        )
    }

    /** 集中提供 `PixelColorScheme` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelColorScheme` 的 `Dark` 配置或运行值。
 *
 * Dark preset that preserves the original PixelThemeData.Default palette exactly.
 */
        public val Dark: PixelColorScheme = PixelColorScheme(
            background = PixelColor.Black,
            onBackground = PixelColor.White,
            surface = PixelColor.Black,
            onSurface = PixelColor.White,
            surfaceVariant = PixelColor.fromRgb(60, 60, 60),
            onSurfaceVariant = PixelColor.fromRgb(160, 160, 160),
            outline = PixelColor.White,
            outlineVariant = PixelColor.fromRgb(120, 120, 120),
            primary = PixelColor.fromRgb(80, 180, 110),
            onPrimary = PixelColor.Black,
            danger = PixelColor.fromRgb(220, 90, 80),
            onDanger = PixelColor.Black,
            warning = PixelColor.fromRgb(255, 200, 0),
            onWarning = PixelColor.Black,
            disabled = PixelColor.fromRgb(80, 80, 80),
            onDisabled = PixelColor.fromRgb(80, 80, 80),
            inactive = PixelColor.fromRgb(120, 120, 120),
            track = PixelColor.fromRgb(60, 60, 60),
            focus = PixelColor.fromRgb(255, 200, 0),
            selection = PixelColor.fromRgb(255, 255, 0),
            scrim = PixelColor.fromArgb(160, 0, 0, 0),
            shadow = PixelColor.Black,
        )

        /** 公开 `PixelColorScheme` 的 `Light` 配置或运行值。
 *
 * Light preset for applications rendered on a bright pixel canvas.
 */
        public val Light: PixelColorScheme = PixelColorScheme(
            background = PixelColor.fromRgb(245, 245, 245),
            onBackground = PixelColor.fromRgb(16, 16, 16),
            surface = PixelColor.White,
            onSurface = PixelColor.Black,
            surfaceVariant = PixelColor.fromRgb(224, 224, 224),
            onSurfaceVariant = PixelColor.fromRgb(72, 72, 72),
            outline = PixelColor.fromRgb(48, 48, 48),
            outlineVariant = PixelColor.fromRgb(128, 128, 128),
            primary = PixelColor.fromRgb(46, 125, 50),
            onPrimary = PixelColor.White,
            danger = PixelColor.fromRgb(160, 32, 32),
            onDanger = PixelColor.White,
            warning = PixelColor.fromRgb(112, 80, 0),
            onWarning = PixelColor.White,
            disabled = PixelColor.fromRgb(208, 208, 208),
            onDisabled = PixelColor.fromRgb(72, 72, 72),
            inactive = PixelColor.fromRgb(96, 96, 96),
            track = PixelColor.fromRgb(176, 176, 176),
            focus = PixelColor.fromRgb(92, 56, 0),
            selection = PixelColor.fromRgb(0, 80, 176),
            scrim = PixelColor.fromArgb(128, 0, 0, 0),
            shadow = PixelColor.Black,
        )

        /** 公开 `PixelColorScheme` 的 `HighContrastDark` 配置或运行值。
 *
 * High-contrast dark preset with light text and a cyan focus indicator.
 */
        public val HighContrastDark: PixelColorScheme = PixelColorScheme(
            background = PixelColor.Black,
            onBackground = PixelColor.White,
            surface = PixelColor.Black,
            onSurface = PixelColor.White,
            surfaceVariant = PixelColor.fromRgb(32, 32, 32),
            onSurfaceVariant = PixelColor.White,
            outline = PixelColor.White,
            outlineVariant = PixelColor.fromRgb(208, 208, 208),
            primary = PixelColor.fromRgb(0, 255, 102),
            onPrimary = PixelColor.Black,
            danger = PixelColor.fromRgb(255, 102, 102),
            onDanger = PixelColor.Black,
            warning = PixelColor.fromRgb(255, 255, 0),
            onWarning = PixelColor.Black,
            disabled = PixelColor.fromRgb(48, 48, 48),
            onDisabled = PixelColor.White,
            inactive = PixelColor.fromRgb(208, 208, 208),
            track = PixelColor.fromRgb(80, 80, 80),
            focus = PixelColor.fromRgb(0, 255, 255),
            selection = PixelColor.fromRgb(255, 255, 0),
            scrim = PixelColor.fromArgb(192, 0, 0, 0),
            shadow = PixelColor.White,
        )

        /** 公开 `PixelColorScheme` 的 `HighContrastLight` 配置或运行值。
 *
 * High-contrast light preset with dark text and a dark-blue focus indicator.
 */
        public val HighContrastLight: PixelColorScheme = PixelColorScheme(
            background = PixelColor.White,
            onBackground = PixelColor.Black,
            surface = PixelColor.White,
            onSurface = PixelColor.Black,
            surfaceVariant = PixelColor.fromRgb(224, 224, 224),
            onSurfaceVariant = PixelColor.Black,
            outline = PixelColor.Black,
            outlineVariant = PixelColor.fromRgb(48, 48, 48),
            primary = PixelColor.fromRgb(0, 75, 29),
            onPrimary = PixelColor.White,
            danger = PixelColor.fromRgb(139, 0, 0),
            onDanger = PixelColor.White,
            warning = PixelColor.fromRgb(107, 77, 0),
            onWarning = PixelColor.White,
            disabled = PixelColor.fromRgb(224, 224, 224),
            onDisabled = PixelColor.Black,
            inactive = PixelColor.fromRgb(48, 48, 48),
            track = PixelColor.fromRgb(160, 160, 160),
            focus = PixelColor.fromRgb(0, 0, 170),
            selection = PixelColor.fromRgb(0, 71, 171),
            scrim = PixelColor.fromArgb(160, 0, 0, 0),
            shadow = PixelColor.Black,
        )

        /** 公开 `PixelColorScheme` 的 `Default` 配置或运行值。
 *
 * Default scheme retained for source compatibility; it is identical to [Dark].
 */
        public val Default: PixelColorScheme = Dark

        /** 创建或解析 `PixelColorScheme` 的 `fromLegacy` 结果，并在返回前校验输入。
 *
 * Creates a semantic scheme from the legacy PixelTheme palette.
 */
        public fun fromLegacy(colors: PixelThemeColors): PixelColorScheme {
            return Dark.copy(
                background = colors.background,
                onBackground = colors.text,
                surface = colors.surface,
                onSurface = colors.text,
                surfaceVariant = colors.track,
                onSurfaceVariant = colors.mutedText,
                outline = colors.border,
                outlineVariant = colors.inactive,
                primary = colors.accent,
                danger = colors.danger,
                warning = colors.warning,
                disabled = colors.disabled,
                onDisabled = colors.disabled,
                inactive = colors.inactive,
                track = colors.track,
                focus = colors.focus,
                selection = colors.selection,
            )
        }
    }
}
