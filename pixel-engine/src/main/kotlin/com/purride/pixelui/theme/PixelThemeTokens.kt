package com.purride.pixelui

import com.purride.pixelcore.PixelTextRasterizer

/**
 * 定义 `PixelTypographyToken` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Role-aware typography token that resolves to the concrete [PixelTextStyle] rendering model.
 *
 * @property colorRole Semantic text color resolved from the current scheme.
 * @property textRasterizer Optional application-provided pixel glyph rasterizer.
 * @property lineSpacing Additional logical pixels between lines; must be non-negative.
 * @property letterSpacing Additional logical pixels between glyphs; must be non-negative.
 * @property lineHeight Optional fixed line height; when present it must be positive.
 * @property fontScale Integer pixel scale; must be positive.
 */
public data class PixelTypographyToken(
    public val colorRole: PixelColorRole = PixelColorRole.OnBackground,
    public val textRasterizer: PixelTextRasterizer? = null,
    public val lineSpacing: Int = 0,
    public val letterSpacing: Int = 0,
    public val lineHeight: Int? = null,
    public val fontScale: Int = 1,
) {
    init {
        requireNonNegativeToken("PixelTypographyToken.lineSpacing", lineSpacing)
        requireNonNegativeToken("PixelTypographyToken.letterSpacing", letterSpacing)
        lineHeight?.let { value -> requirePositiveToken("PixelTypographyToken.lineHeight", value) }
        requirePositiveToken("PixelTypographyToken.fontScale", fontScale)
    }

    /** 查询 `PixelThemeTokens` 的 `resolve` 结果，不产生额外状态变更。
 *
 * Resolves this semantic token to a concrete [PixelTextStyle] using [colors].
 */
    public fun resolve(colors: PixelColorScheme): PixelTextStyle {
        return PixelTextStyle(
            color = colors.resolve(colorRole),
            textRasterizer = textRasterizer,
            lineSpacing = lineSpacing,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            fontScale = fontScale,
        )
    }

    /** 集中提供 `PixelThemeTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelThemeTokens` 的 `Default` 配置或运行值。
 *
 * Default body-text typography token.
 */
        public val Default: PixelTypographyToken = PixelTypographyToken()
    }
}

/**
 * 定义 `PixelTypographyTokens` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Typography roles shared by standard components.
 *
 * @property body Default application body text.
 * @property label Compact control and metadata labels.
 * @property title Dialog, sheet, and section titles.
 * @property caption Secondary and placeholder text.
 * @property button Button foreground text.
 * @property input Editable input text.
 */
public data class PixelTypographyTokens(
    public val body: PixelTypographyToken = PixelTypographyToken.Default,
    public val label: PixelTypographyToken = PixelTypographyToken(
        colorRole = PixelColorRole.OnSurface,
    ),
    public val title: PixelTypographyToken = PixelTypographyToken.Default,
    public val caption: PixelTypographyToken = PixelTypographyToken(
        colorRole = PixelColorRole.OnSurfaceVariant,
    ),
    public val button: PixelTypographyToken = PixelTypographyToken.Default,
    public val input: PixelTypographyToken = PixelTypographyToken.Default,
) {
    /** 集中提供 `PixelThemeTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelThemeTokens` 的 `Default` 配置或运行值。
 *
 * Default one-pixel typography scale.
 */
        public val Default: PixelTypographyTokens = PixelTypographyTokens()
    }
}

/**
 * 定义 `PixelSpacingTokens` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Pixel-aligned spacing scale.
 *
 * @property none Zero spacing.
 * @property extraSmall One-pixel spacing.
 * @property small Compact spacing.
 * @property medium Standard component spacing.
 * @property large Section spacing.
 * @property extraLarge Large layout separation.
 */
public data class PixelSpacingTokens(
    public val none: Int = 0,
    public val extraSmall: Int = 1,
    public val small: Int = 2,
    public val medium: Int = 4,
    public val large: Int = 8,
    public val extraLarge: Int = 12,
) {
    init {
        validateNonNegativeTokens(
            owner = "PixelSpacingTokens",
            "none" to none,
            "extraSmall" to extraSmall,
            "small" to small,
            "medium" to medium,
            "large" to large,
            "extraLarge" to extraLarge,
        )
    }

    /** 集中提供 `PixelThemeTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelThemeTokens` 的 `Default` 配置或运行值。
 *
 * Default spacing scale for one-logical-pixel rendering.
 */
        public val Default: PixelSpacingTokens = PixelSpacingTokens()
    }
}

/**
 * 定义 `PixelSizeTokens` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Standard component and icon size scale.
 *
 * @property iconSmall Small icon extent.
 * @property iconMedium Standard icon extent.
 * @property iconLarge Large icon extent.
 * @property selectionControlExtent Checkbox and compact selection-control extent.
 * @property switchWidth Standard compact Switch width.
 * @property trackHeight Slider, progress, and Switch track height.
 * @property compactControlHeight Compact control height.
 * @property controlHeight Standard control height.
 * @property touchTarget Minimum recommended pointer target extent.
 * @property overlayMinimumWidth Minimum menu and popover width.
 */
public data class PixelSizeTokens(
    public val iconSmall: Int = 8,
    public val iconMedium: Int = 12,
    public val iconLarge: Int = 16,
    public val selectionControlExtent: Int = 9,
    public val switchWidth: Int = 14,
    public val trackHeight: Int = 7,
    public val compactControlHeight: Int = 12,
    public val controlHeight: Int = 16,
    public val touchTarget: Int = 24,
    public val overlayMinimumWidth: Int = 40,
) {
    init {
        validatePositiveTokens(
            owner = "PixelSizeTokens",
            "iconSmall" to iconSmall,
            "iconMedium" to iconMedium,
            "iconLarge" to iconLarge,
            "selectionControlExtent" to selectionControlExtent,
            "switchWidth" to switchWidth,
            "trackHeight" to trackHeight,
            "compactControlHeight" to compactControlHeight,
            "controlHeight" to controlHeight,
            "touchTarget" to touchTarget,
            "overlayMinimumWidth" to overlayMinimumWidth,
        )
    }

    /** 集中提供 `PixelThemeTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelThemeTokens` 的 `Default` 配置或运行值。
 *
 * Default positive component size scale.
 */
        public val Default: PixelSizeTokens = PixelSizeTokens()
    }
}

/**
 * 定义 `PixelRadiusTokens` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Stair-step corner radii that preserve integer pixel boundaries.
 *
 * @property none Square corner radius.
 * @property small Small stair-step radius.
 * @property medium Standard stair-step radius.
 * @property large Large stair-step radius.
 * @property pill Large radius used when geometry should cap it to half the component extent.
 */
public data class PixelRadiusTokens(
    public val none: Int = 0,
    public val small: Int = 1,
    public val medium: Int = 2,
    public val large: Int = 4,
    public val pill: Int = 999,
) {
    init {
        validateNonNegativeTokens(
            owner = "PixelRadiusTokens",
            "none" to none,
            "small" to small,
            "medium" to medium,
            "large" to large,
            "pill" to pill,
        )
    }

    /** 集中提供 `PixelThemeTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelThemeTokens` 的 `Default` 配置或运行值。
 *
 * Default non-negative stair-step radius scale.
 */
        public val Default: PixelRadiusTokens = PixelRadiusTokens()
    }
}

/**
 * 定义 `PixelBorderTokens` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Integer border widths used by standard components.
 *
 * @property none No border.
 * @property thin Standard one-pixel border.
 * @property thick Emphasized border.
 * @property focus Focus-indicator border width.
 */
public data class PixelBorderTokens(
    public val none: Int = 0,
    public val thin: Int = 1,
    public val thick: Int = 2,
    public val focus: Int = 1,
) {
    init {
        requireNonNegativeToken("PixelBorderTokens.none", none)
        requirePositiveToken("PixelBorderTokens.thin", thin)
        requirePositiveToken("PixelBorderTokens.thick", thick)
        requirePositiveToken("PixelBorderTokens.focus", focus)
    }

    /** 集中提供 `PixelThemeTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelThemeTokens` 的 `Default` 配置或运行值。
 *
 * Default one- and two-pixel border scale.
 */
        public val Default: PixelBorderTokens = PixelBorderTokens()
    }
}

/** 定义 `PixelElevationRole` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Semantic hard-shadow levels referenced by component tokens.
 */
public enum class PixelElevationRole {
    /** Surface without a shadow offset. */
    None,

    /** Low-emphasis one-pixel shadow. */
    Low,

    /** Standard overlay shadow. */
    Medium,

    /** High-emphasis modal shadow. */
    High,
}

/**
 * 定义 `PixelElevationTokens` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Hard-edged elevation offsets rendered without blur or anti-aliasing.
 *
 * @property none Surface without a shadow offset.
 * @property low One-pixel shadow offset.
 * @property medium Standard shadow offset.
 * @property high High-emphasis overlay shadow offset.
 */
public data class PixelElevationTokens(
    public val none: Int = 0,
    public val low: Int = 1,
    public val medium: Int = 2,
    public val high: Int = 4,
) {
    init {
        validateNonNegativeTokens(
            owner = "PixelElevationTokens",
            "none" to none,
            "low" to low,
            "medium" to medium,
            "high" to high,
        )
    }

    /** 查询 `PixelThemeTokens` 的 `resolve` 结果，不产生额外状态变更。
 *
 * Resolves a semantic [role] to its integer hard-shadow offset.
 */
    public fun resolve(role: PixelElevationRole): Int {
        return when (role) {
            PixelElevationRole.None -> none
            PixelElevationRole.Low -> low
            PixelElevationRole.Medium -> medium
            PixelElevationRole.High -> high
        }
    }

    /** 集中提供 `PixelThemeTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelThemeTokens` 的 `Default` 配置或运行值。
 *
 * Default hard-shadow offset scale.
 */
        public val Default: PixelElevationTokens = PixelElevationTokens()
    }
}

/** 定义 `PixelThemeBrightness` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Overall lightness mode declared by a [PixelThemeTokens] preset.
 */
public enum class PixelThemeBrightness {
    /** Bright surfaces with dark foreground content. */
    Light,

    /** Dark surfaces with light foreground content. */
    Dark,
}

/** 定义 `PixelThemeContrast` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Contrast policy declared by a [PixelThemeTokens] preset.
 */
public enum class PixelThemeContrast {
    /** Standard application contrast. */
    Standard,

    /** Enhanced text, outline, focus, and state contrast. */
    High,
}

/**
 * 定义 `PixelThemeTokens` 在 `PixelThemeTokens` 中承担的数据与行为边界。
 *
 * Complete immutable Pixel UI theme token graph.
 *
 * This is the only theme model provided by [PixelTheme]; components read it with
 * `PixelTheme.of(context)`.
 *
 * @property brightness Declared light or dark surface mode.
 * @property contrast Declared standard or high-contrast policy.
 * @property colors Concrete semantic color scheme.
 * @property typography Role-aware typography tokens.
 * @property spacing Shared spacing scale.
 * @property sizes Shared icon and control size scale.
 * @property radii Stair-step corner radius scale.
 * @property borders Integer border width scale.
 * @property elevations Hard-edged shadow offset scale.
 * @property motion Shared component motion tokens.
 * @property components Per-component semantic role and geometry tokens.
 * @property labels Localizable standard component fallback labels.
 */
public data class PixelThemeTokens(
    public val brightness: PixelThemeBrightness = PixelThemeBrightness.Dark,
    public val contrast: PixelThemeContrast = PixelThemeContrast.Standard,
    public val colors: PixelColorScheme = PixelColorScheme.Dark,
    public val typography: PixelTypographyTokens = PixelTypographyTokens.Default,
    public val spacing: PixelSpacingTokens = PixelSpacingTokens.Default,
    public val sizes: PixelSizeTokens = PixelSizeTokens.Default,
    public val radii: PixelRadiusTokens = PixelRadiusTokens.Default,
    public val borders: PixelBorderTokens = PixelBorderTokens.Default,
    public val elevations: PixelElevationTokens = PixelElevationTokens.Default,
    public val motion: PixelMotionThemeData = PixelMotionThemeData.Default,
    public val components: PixelComponentTokens = PixelComponentTokens.Default,
    public val labels: PixelLabelTokens = PixelLabelTokens.Default,
) {
    /** 集中提供 `PixelThemeTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelThemeTokens` 的 `Dark` 配置或运行值。
 *
 * Dark preset used as the default visual palette.
 */
        public val Dark: PixelThemeTokens = PixelThemeTokens(
            brightness = PixelThemeBrightness.Dark,
            contrast = PixelThemeContrast.Standard,
            colors = PixelColorScheme.Dark,
        )

        /** 公开 `PixelThemeTokens` 的 `Light` 配置或运行值。
 *
 * Light preset with dark content on bright surfaces.
 */
        public val Light: PixelThemeTokens = PixelThemeTokens(
            brightness = PixelThemeBrightness.Light,
            contrast = PixelThemeContrast.Standard,
            colors = PixelColorScheme.Light,
        )

        /** 公开 `PixelThemeTokens` 的 `HighContrastDark` 配置或运行值。
 *
 * High-contrast dark preset with maximum text contrast and a cyan focus indicator.
 */
        public val HighContrastDark: PixelThemeTokens = PixelThemeTokens(
            brightness = PixelThemeBrightness.Dark,
            contrast = PixelThemeContrast.High,
            colors = PixelColorScheme.HighContrastDark,
            borders = PixelBorderTokens.Default.copy(focus = 2),
        )

        /** 公开 `PixelThemeTokens` 的 `HighContrastLight` 配置或运行值。
 *
 * High-contrast light preset with maximum text contrast and a dark-blue focus indicator.
 */
        public val HighContrastLight: PixelThemeTokens = PixelThemeTokens(
            brightness = PixelThemeBrightness.Light,
            contrast = PixelThemeContrast.High,
            colors = PixelColorScheme.HighContrastLight,
            borders = PixelBorderTokens.Default.copy(focus = 2),
        )

        /** 公开 `PixelThemeTokens` 的 `Default` 配置或运行值。
 *
 * Default token graph retained as the original dark visual theme.
 */
        public val Default: PixelThemeTokens = Dark

        /**
 * 执行 `PixelThemeTokens` 的 `forCapabilities` 公开行为；具体参数、返回和副作用见下文。
 *
         * Selects a built-in light or dark preset from an immutable Host capability snapshot.
         *
         * This pure overload is useful for setup code and tests that already own a complete
         * snapshot. Widget build methods should prefer [forHost] so high-contrast updates register
         * an inherited dependency automatically.
         */
        public fun forCapabilities(
            /** Complete environment snapshot containing the high-contrast preference. */
            capabilities: HostCapabilitiesData,
            /** Requested surface brightness retained across contrast changes. */
            brightness: PixelThemeBrightness = PixelThemeBrightness.Dark,
        ): PixelThemeTokens {
            return when (brightness) {
                PixelThemeBrightness.Light -> if (capabilities.highContrast) HighContrastLight else Light
                PixelThemeBrightness.Dark -> if (capabilities.highContrast) HighContrastDark else Dark
            }
        }

        /**
 * 执行 `PixelThemeTokens` 的 `forHost` 公开行为；具体参数、返回和副作用见下文。
 *
         * Selects a built-in preset and subscribes the caller to Host high-contrast changes.
         */
        public fun forHost(
            /** Build context receiving the nearest complete Host capability snapshot. */
            context: BuildContext,
            /** Requested surface brightness retained across contrast changes. */
            brightness: PixelThemeBrightness = PixelThemeBrightness.Dark,
        ): PixelThemeTokens {
            return forCapabilities(
                capabilities = HostCapabilities.of(context),
                brightness = brightness,
            )
        }
    }
}

/** Validates a group of named integer tokens that permit zero. */
private fun validateNonNegativeTokens(owner: String, vararg values: Pair<String, Int>) {
    values.forEach { (name, value) -> requireNonNegativeToken("$owner.$name", value) }
}

/** Validates a group of named integer tokens that must be positive. */
private fun validatePositiveTokens(owner: String, vararg values: Pair<String, Int>) {
    values.forEach { (name, value) -> requirePositiveToken("$owner.$name", value) }
}
