package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * 定义 `PixelFocusIndicatorTokens` 在 `PixelComponentTokens` 中承担的数据与行为边界。
 *
 * Additive focus-indicator tokens kept separate from single-value component state resolution.
 *
 * A focused component may therefore retain its selected, error, or pressed base colors while
 * drawing this indicator as an independent pixel-aligned layer.
 *
 * @property colorRole Semantic color resolved from the active [PixelColorScheme].
 * @property width Indicator width encoding. The standard value `1` resolves through
 * [PixelBorderTokens.focus]; uncommon positive values remain literal logical pixels.
 * @property inset Distance from the component edge in logical pixels; must be non-negative.
 */
public data class PixelFocusIndicatorTokens(
    public val colorRole: PixelColorRole = PixelColorRole.Focus,
    public val width: Int = 1,
    public val inset: Int = 0,
) {
    init {
        requirePositiveToken("PixelFocusIndicatorTokens.width", width)
        requireNonNegativeToken("PixelFocusIndicatorTokens.inset", inset)
    }

    /** 查询 `PixelComponentTokens` 的 `resolveColor` 结果，不产生额外状态变更。
 *
 * Resolves this indicator's semantic color against [colors].
 */
    public fun resolveColor(colors: PixelColorScheme): PixelColor = colors.resolve(colorRole)

    /** 查询 `PixelComponentTokens` 的 `resolveWidth` 结果，不产生额外状态变更。
 *
 * Resolves the standard focus-width encoding while preserving uncommon literal widths.
 */
    public fun resolveWidth(borders: PixelBorderTokens): Int {
        return if (width == DEFAULT_FOCUS_WIDTH_ENCODING) borders.focus else width
    }

    /** 集中提供 `PixelComponentTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** Standard encoding delegated to the active foundation focus-border token. */
        private const val DEFAULT_FOCUS_WIDTH_ENCODING: Int = 1

        /** 公开 `PixelComponentTokens` 的 `Default` 配置或运行值。
 *
 * Default one-pixel focus indicator using [PixelColorRole.Focus].
 */
        public val Default: PixelFocusIndicatorTokens = PixelFocusIndicatorTokens()
    }
}

/**
 * 定义 `PixelComponentColorTokens` 在 `PixelComponentTokens` 中承担的数据与行为边界。
 *
 * Semantic visual and geometry tokens shared by one standard component family.
 *
 * Concrete colors are intentionally absent: [containerColor], [contentColor], and [borderColor]
 * retain semantic roles and resolve through the latest scheme at build time. Null means that the
 * corresponding layer is not painted. [focusIndicator] is additive and is never folded into the
 * priority chain used by those three state properties.
 *
 * @property containerColor State-dependent fill color role, or null for no fill.
 * @property contentColor State-dependent foreground color role, or null for no foreground.
 * @property borderColor State-dependent border color role, or null for no border.
 * @property focusIndicator Independent focus layer, or null for non-focusable visuals.
 * @property padding Pixel-aligned content padding encoded against [PixelSpacingTokens].
 * @property minimumWidth Minimum logical width; must be non-negative.
 * @property minimumHeight Minimum logical height; must be non-negative.
 * @property borderWidth Border width encoded against [PixelBorderTokens]; must be non-negative.
 * @property cornerRadius Stair-step radius encoded against [PixelRadiusTokens]; must be non-negative.
 * @property elevationRole Semantic hard-shadow level resolved from [PixelElevationTokens].
 */
public data class PixelComponentColorTokens(
    public val containerColor: PixelStateProperty<PixelColorRole?> = PixelStateProperty.constant(null),
    public val contentColor: PixelStateProperty<PixelColorRole?> = PixelStateProperty.constant(
        PixelColorRole.OnSurface,
    ),
    public val borderColor: PixelStateProperty<PixelColorRole?> = PixelStateProperty.constant(
        PixelColorRole.Outline,
    ),
    // Construct directly so PixelThemeTokens-first class initialization cannot observe a null
    // companion field while PixelFocusIndicatorTokens.Default is still being initialized.
    public val focusIndicator: PixelFocusIndicatorTokens? = PixelFocusIndicatorTokens(),
    public val padding: EdgeInsets = EdgeInsets.all(0),
    public val minimumWidth: Int = 0,
    public val minimumHeight: Int = 0,
    public val borderWidth: Int = 1,
    public val cornerRadius: Int = 0,
    public val elevationRole: PixelElevationRole = PixelElevationRole.None,
) {
    init {
        requireNonNegativeToken("PixelComponentColorTokens.padding.left", padding.left)
        requireNonNegativeToken("PixelComponentColorTokens.padding.top", padding.top)
        requireNonNegativeToken("PixelComponentColorTokens.padding.right", padding.right)
        requireNonNegativeToken("PixelComponentColorTokens.padding.bottom", padding.bottom)
        requireNonNegativeToken("PixelComponentColorTokens.minimumWidth", minimumWidth)
        requireNonNegativeToken("PixelComponentColorTokens.minimumHeight", minimumHeight)
        requireNonNegativeToken("PixelComponentColorTokens.borderWidth", borderWidth)
        requireNonNegativeToken("PixelComponentColorTokens.cornerRadius", cornerRadius)
    }

    /** 查询 `PixelComponentTokens` 的 `resolveContainerColor` 结果，不产生额外状态变更。
 *
 * Resolves the container color from [states] and [colors], retaining null as no fill.
 */
    public fun resolveContainerColor(
        states: PixelControlStateSet,
        colors: PixelColorScheme,
    ): PixelColor? = containerColor.resolve(states)?.let(colors::resolve)

    /** 查询 `PixelComponentTokens` 的 `resolveContentColor` 结果，不产生额外状态变更。
 *
 * Resolves the content color from [states] and [colors], retaining null as no foreground.
 */
    public fun resolveContentColor(
        states: PixelControlStateSet,
        colors: PixelColorScheme,
    ): PixelColor? = contentColor.resolve(states)?.let(colors::resolve)

    /** 查询 `PixelComponentTokens` 的 `resolveBorderColor` 结果，不产生额外状态变更。
 *
 * Resolves the border color from [states] and [colors], retaining null as no border.
 */
    public fun resolveBorderColor(
        states: PixelControlStateSet,
        colors: PixelColorScheme,
    ): PixelColor? = borderColor.resolve(states)?.let(colors::resolve)

    /** 执行 `PixelComponentTokens` 的 `focusIndicatorFor` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the additive focus token only while [states] contains Focused.
 */
    public fun focusIndicatorFor(states: PixelControlStateSet): PixelFocusIndicatorTokens? {
        return focusIndicator?.takeIf { PixelControlState.Focused in states }
    }

    /** 查询 `PixelComponentTokens` 的 `resolveElevation` 结果，不产生额外状态变更。
 *
 * Resolves this component's semantic hard-shadow level through [elevations].
 */
    public fun resolveElevation(elevations: PixelElevationTokens): Int {
        return elevations.resolve(elevationRole)
    }

    /** 查询 `PixelComponentTokens` 的 `resolvePadding` 结果，不产生额外状态变更。
 *
 * Resolves every standard spacing-scale edge while retaining uncommon literal pixel values.
 */
    public fun resolvePadding(spacing: PixelSpacingTokens): EdgeInsets {
        return EdgeInsets(
            left = resolveSpacingValue(padding.left, spacing),
            top = resolveSpacingValue(padding.top, spacing),
            right = resolveSpacingValue(padding.right, spacing),
            bottom = resolveSpacingValue(padding.bottom, spacing),
        )
    }

    /** 查询 `PixelComponentTokens` 的 `resolveBorderWidth` 结果，不产生额外状态变更。
 *
 * Resolves the standard none/thin/thick encoding through [borders].
 */
    public fun resolveBorderWidth(borders: PixelBorderTokens): Int {
        return when (borderWidth) {
            0 -> borders.none
            1 -> borders.thin
            2 -> borders.thick
            else -> borderWidth
        }
    }

    /** 查询 `PixelComponentTokens` 的 `resolveCornerRadius` 结果，不产生额外状态变更。
 *
 * Resolves standard radius-scale encodings while retaining uncommon literal pixel values.
 */
    public fun resolveCornerRadius(radii: PixelRadiusTokens): Int {
        return when (cornerRadius) {
            0 -> radii.none
            1 -> radii.small
            2 -> radii.medium
            4 -> radii.large
            999 -> radii.pill
            else -> cornerRadius
        }
    }

    /** 查询 `PixelComponentTokens` 的 `resolveMinimumWidth` 结果，不产生额外状态变更。
 *
 * Resolves a standard minimum-width encoding through the shared component size scale.
 */
    public fun resolveMinimumWidth(sizes: PixelSizeTokens): Int {
        return resolveComponentSizeValue(minimumWidth, sizes)
    }

    /** 查询 `PixelComponentTokens` 的 `resolveMinimumHeight` 结果，不产生额外状态变更。
 *
 * Resolves a standard minimum-height encoding through the shared component size scale.
 */
    public fun resolveMinimumHeight(sizes: PixelSizeTokens): Int {
        return resolveComponentSizeValue(minimumHeight, sizes)
    }

    /** 集中提供 `PixelComponentTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelComponentTokens` 的 `Button` 配置或运行值。
 *
 * Outlined button defaults with full interaction and semantic state roles.
 */
        public val Button: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = colorRoles(
                normal = null,
                PixelControlState.Hovered to PixelColorRole.SurfaceVariant,
                PixelControlState.Pressed to PixelColorRole.Primary,
                PixelControlState.Selected to PixelColorRole.Primary,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            contentColor = colorRoles(
                normal = PixelColorRole.OnBackground,
                PixelControlState.Hovered to PixelColorRole.OnSurface,
                PixelControlState.Pressed to PixelColorRole.OnPrimary,
                PixelControlState.Selected to PixelColorRole.OnPrimary,
                PixelControlState.Disabled to PixelColorRole.OnDisabled,
                PixelControlState.Error to PixelColorRole.OnDanger,
                PixelControlState.Loading to PixelColorRole.OnWarning,
            ),
            borderColor = colorRoles(
                normal = PixelColorRole.Outline,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
            ),
            padding = EdgeInsets.all(2),
            cornerRadius = 1,
        )

        /** 公开 `PixelComponentTokens` 的 `TextButton` 配置或运行值。
 *
 * Borderless text-button defaults with foreground-only interaction feedback.
 */
        public val TextButton: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = PixelStateProperty.constant(null),
            contentColor = colorRoles(
                normal = PixelColorRole.OnBackground,
                PixelControlState.Hovered to PixelColorRole.Primary,
                PixelControlState.Pressed to PixelColorRole.Selection,
                PixelControlState.Selected to PixelColorRole.Primary,
                PixelControlState.Disabled to PixelColorRole.OnDisabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            borderColor = PixelStateProperty.constant(null),
            borderWidth = 0,
        )

        /** 公开 `PixelComponentTokens` 的 `TextField` 配置或运行值。
 *
 * Text-field defaults including focus, error, loading, and disabled outlines.
 */
        public val TextField: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = PixelStateProperty.constant(null),
            contentColor = colorRoles(
                normal = PixelColorRole.OnBackground,
                PixelControlState.Disabled to PixelColorRole.OnDisabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            borderColor = colorRoles(
                normal = PixelColorRole.Outline,
                PixelControlState.Focused to PixelColorRole.Selection,
                PixelControlState.Selected to PixelColorRole.Selection,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            padding = EdgeInsets.all(2),
            cornerRadius = 1,
        )

        /** 公开 `PixelComponentTokens` 的 `SelectionControl` 配置或运行值。
 *
 * Selection-control defaults used by checkbox, switch, tabs, and segmented controls.
 */
        public val SelectionControl: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = colorRoles(
                normal = PixelColorRole.Surface,
                PixelControlState.Hovered to PixelColorRole.SurfaceVariant,
                PixelControlState.Pressed to PixelColorRole.Primary,
                PixelControlState.Selected to PixelColorRole.Primary,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            contentColor = colorRoles(
                normal = PixelColorRole.OnSurface,
                PixelControlState.Selected to PixelColorRole.OnPrimary,
                PixelControlState.Pressed to PixelColorRole.OnPrimary,
                PixelControlState.Disabled to PixelColorRole.OnDisabled,
                PixelControlState.Error to PixelColorRole.OnDanger,
                PixelControlState.Loading to PixelColorRole.OnWarning,
            ),
            borderColor = colorRoles(
                normal = PixelColorRole.Outline,
                PixelControlState.Selected to PixelColorRole.Primary,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
            ),
            cornerRadius = 1,
        )

        /** 公开 `PixelComponentTokens` 的 `TrackControl` 配置或运行值。
 *
 * Slider and progress defaults with a track container and primary active content.
 */
        public val TrackControl: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = colorRoles(
                normal = PixelColorRole.Track,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            contentColor = colorRoles(
                normal = PixelColorRole.Primary,
                PixelControlState.Hovered to PixelColorRole.Warning,
                PixelControlState.Pressed to PixelColorRole.Selection,
                PixelControlState.Selected to PixelColorRole.Primary,
                PixelControlState.Disabled to PixelColorRole.OnDisabled,
                PixelControlState.Error to PixelColorRole.OnDanger,
                PixelControlState.Loading to PixelColorRole.OnWarning,
            ),
            borderColor = PixelStateProperty.constant(null),
            minimumHeight = 7,
            borderWidth = 0,
        )

        /** 公开 `PixelComponentTokens` 的 `SurfaceControl` 配置或运行值。
 *
 * Standard outlined surface used by lists, adjusters, menus, and dropdowns.
 */
        public val SurfaceControl: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = colorRoles(
                normal = PixelColorRole.Surface,
                PixelControlState.Hovered to PixelColorRole.SurfaceVariant,
                PixelControlState.Pressed to PixelColorRole.SurfaceVariant,
                PixelControlState.Selected to PixelColorRole.Primary,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            contentColor = colorRoles(
                normal = PixelColorRole.OnSurface,
                PixelControlState.Selected to PixelColorRole.OnPrimary,
                PixelControlState.Disabled to PixelColorRole.OnDisabled,
                PixelControlState.Error to PixelColorRole.OnDanger,
                PixelControlState.Loading to PixelColorRole.OnWarning,
            ),
            borderColor = colorRoles(
                normal = PixelColorRole.Outline,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
            ),
            padding = EdgeInsets.all(1),
            cornerRadius = 1,
        )

        /** 公开 `PixelComponentTokens` 的 `Overlay` 配置或运行值。
 *
 * Passive overlay surface used by dialogs, sheets, and tooltips.
 */
        public val Overlay: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = colorRoles(
                normal = PixelColorRole.Surface,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            contentColor = colorRoles(
                normal = PixelColorRole.OnSurface,
                PixelControlState.Disabled to PixelColorRole.OnDisabled,
                PixelControlState.Error to PixelColorRole.OnDanger,
                PixelControlState.Loading to PixelColorRole.OnWarning,
            ),
            borderColor = colorRoles(
                normal = PixelColorRole.Outline,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            focusIndicator = null,
            padding = EdgeInsets.all(2),
            cornerRadius = 1,
            elevationRole = PixelElevationRole.Medium,
        )

        /** 公开 `PixelComponentTokens` 的 `Notification` 配置或运行值。
 *
 * Passive notification surface used by toast and snackbar content.
 */
        public val Notification: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = colorRoles(
                normal = PixelColorRole.Surface,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            contentColor = colorRoles(
                normal = PixelColorRole.OnSurface,
                PixelControlState.Error to PixelColorRole.OnDanger,
                PixelControlState.Loading to PixelColorRole.OnWarning,
            ),
            borderColor = PixelStateProperty.constant(PixelColorRole.Outline),
            focusIndicator = null,
            padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
            cornerRadius = 1,
            elevationRole = PixelElevationRole.Low,
        )

        /** 公开 `PixelComponentTokens` 的 `Scrollbar` 配置或运行值。
 *
 * Narrow scroll indicator with no border or focus layer.
 */
        public val Scrollbar: PixelComponentColorTokens = PixelComponentColorTokens(
            containerColor = PixelStateProperty.constant(PixelColorRole.Track),
            contentColor = colorRoles(
                normal = PixelColorRole.OnSurface,
                PixelControlState.Hovered to PixelColorRole.Primary,
                PixelControlState.Pressed to PixelColorRole.Selection,
                PixelControlState.Disabled to PixelColorRole.Disabled,
                PixelControlState.Error to PixelColorRole.Danger,
                PixelControlState.Loading to PixelColorRole.Warning,
            ),
            borderColor = PixelStateProperty.constant(null),
            focusIndicator = null,
            minimumWidth = 1,
            borderWidth = 0,
        )

        /** 公开 `PixelComponentTokens` 的 `Default` 配置或运行值。
 *
 * Default generic component token set.
 */
        public val Default: PixelComponentColorTokens = SurfaceControl
    }
}

/**
 * Switch defaults with every legacy paint channel encoded independently in the token graph.
 *
 * The track remains a Surface, while thumb/content and outline retain the historical inactive,
 * primary, focus, capability, and validation endpoints. Keeping these roles in fields makes a
 * geometry-only `copy` visually identical to the unchanged default token object.
 */
private val DEFAULT_SWITCH_COMPONENT_TOKENS: PixelComponentColorTokens = PixelComponentColorTokens(
    containerColor = PixelStateProperty.constant(PixelColorRole.Surface),
    contentColor = colorRoles(
        normal = PixelColorRole.Inactive,
        PixelControlState.Hovered to PixelColorRole.Focus,
        PixelControlState.Pressed to PixelColorRole.Focus,
        PixelControlState.Selected to PixelColorRole.Primary,
        PixelControlState.Disabled to PixelColorRole.OnDisabled,
        PixelControlState.Error to PixelColorRole.OnDanger,
        PixelControlState.Loading to PixelColorRole.OnWarning,
    ),
    borderColor = colorRoles(
        normal = PixelColorRole.Inactive,
        PixelControlState.Hovered to PixelColorRole.Focus,
        PixelControlState.Pressed to PixelColorRole.Focus,
        PixelControlState.Selected to PixelColorRole.Primary,
        PixelControlState.Disabled to PixelColorRole.Disabled,
        PixelControlState.Error to PixelColorRole.Danger,
        PixelControlState.Loading to PixelColorRole.Warning,
    ),
    minimumWidth = 14,
    minimumHeight = 7,
    cornerRadius = 1,
)

/**
 * Tab defaults preserving the legacy transparent strip and on-background label channel.
 *
 * Selection and capability/validation feedback live in border and content maps rather than an
 * object-equality branch, so copying padding or another geometry field cannot alter paint.
 */
private val DEFAULT_TAB_COMPONENT_TOKENS: PixelComponentColorTokens = PixelComponentColorTokens(
    containerColor = PixelStateProperty.constant(null),
    contentColor = colorRoles(
        normal = PixelColorRole.OnBackground,
        PixelControlState.Hovered to PixelColorRole.OnBackground,
        PixelControlState.Pressed to PixelColorRole.OnBackground,
        PixelControlState.Selected to PixelColorRole.OnBackground,
        PixelControlState.Disabled to PixelColorRole.OnDisabled,
        PixelControlState.Error to PixelColorRole.Danger,
        PixelControlState.Loading to PixelColorRole.Warning,
    ),
    borderColor = colorRoles(
        normal = PixelColorRole.Outline,
        PixelControlState.Hovered to PixelColorRole.Focus,
        PixelControlState.Pressed to PixelColorRole.Selection,
        PixelControlState.Selected to PixelColorRole.Primary,
        PixelControlState.Disabled to PixelColorRole.Disabled,
        PixelControlState.Error to PixelColorRole.Danger,
        PixelControlState.Loading to PixelColorRole.Warning,
    ),
    padding = EdgeInsets.all(2),
    cornerRadius = 1,
)

/**
 * 定义 `PixelComponentTokens` 在 `PixelComponentTokens` 中承担的数据与行为边界。
 *
 * Component-level tokens for every standard Pixel UI component family.
 *
 * Every entry contains semantic color roles and pixel geometry only; changing the enclosing
 * [PixelColorScheme] therefore updates all entries without copying component tokens.
 */
public data class PixelComponentTokens(
    /** 公开 `PixelComponentTokens` 的 `button` 配置或运行值。
 *
 * Outlined button tokens.
 */
    public val button: PixelComponentColorTokens = PixelComponentColorTokens.Button,
    /** 公开 `PixelComponentTokens` 的 `textButton` 配置或运行值。
 *
 * Borderless text-button tokens.
 */
    public val textButton: PixelComponentColorTokens = PixelComponentColorTokens.TextButton,
    /** 公开 `PixelComponentTokens` 的 `iconButton` 配置或运行值。
 *
 * Compact icon-button tokens with a full touch-target surface.
 */
    public val iconButton: PixelComponentColorTokens = PixelComponentColorTokens.Button.copy(
        minimumWidth = 24,
        minimumHeight = 24,
    ),
    /** 公开 `PixelComponentTokens` 的 `textField` 配置或运行值。
 *
 * Editable text-field tokens.
 */
    public val textField: PixelComponentColorTokens = PixelComponentColorTokens.TextField,
    /** 公开 `PixelComponentTokens` 的 `listTile` 配置或运行值。
 *
 * List-tile tokens.
 */
    public val listTile: PixelComponentColorTokens = PixelComponentColorTokens.SurfaceControl.copy(
        containerColor = PixelStateProperty.constant(null),
        padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
    ),
    /** 公开 `PixelComponentTokens` 的 `checkbox` 配置或运行值。
 *
 * Checkbox tokens.
 */
    public val checkbox: PixelComponentColorTokens = PixelComponentColorTokens.SelectionControl.copy(
        minimumWidth = 9,
        minimumHeight = 9,
    ),
    /** 公开 `PixelComponentTokens` 的 `radio` 配置或运行值。
 *
 * Radio-button tokens with a circular selection-control surface.
 */
    public val radio: PixelComponentColorTokens = PixelComponentColorTokens.SelectionControl.copy(
        minimumWidth = 9,
        minimumHeight = 9,
        cornerRadius = 999,
    ),
    /** 公开 `PixelComponentTokens` 的 `switch` 配置或运行值。
 *
 * Switch tokens.
 */
    public val switch: PixelComponentColorTokens = DEFAULT_SWITCH_COMPONENT_TOKENS,
    /** 公开 `PixelComponentTokens` 的 `slider` 配置或运行值。
 *
 * Slider tokens.
 */
    public val slider: PixelComponentColorTokens = PixelComponentColorTokens.TrackControl,
    /** 公开 `PixelComponentTokens` 的 `tabs` 配置或运行值。
 *
 * Tab-strip tokens.
 */
    public val tabs: PixelComponentColorTokens = DEFAULT_TAB_COMPONENT_TOKENS,
    /** 公开 `PixelComponentTokens` 的 `segmented` 配置或运行值。
 *
 * Segmented-control tokens.
 */
    public val segmented: PixelComponentColorTokens = PixelComponentColorTokens.SelectionControl.copy(
        padding = EdgeInsets.all(2),
    ),
    /** 公开 `PixelComponentTokens` 的 `navigationBar` 配置或运行值。
 *
 * Bottom application-navigation tokens with a full-width compact control height.
 */
    public val navigationBar: PixelComponentColorTokens = PixelComponentColorTokens.SurfaceControl.copy(
        minimumHeight = 16,
        cornerRadius = 0,
    ),
    /** 公开 `PixelComponentTokens` 的 `navigationRail` 配置或运行值。
 *
 * Side application-navigation tokens with a compact minimum rail width.
 */
    public val navigationRail: PixelComponentColorTokens = PixelComponentColorTokens.SurfaceControl.copy(
        minimumWidth = 16,
        cornerRadius = 0,
    ),
    /** 公开 `PixelComponentTokens` 的 `valueAdjuster` 配置或运行值。
 *
 * Value-adjuster tokens.
 */
    public val valueAdjuster: PixelComponentColorTokens = PixelComponentColorTokens.SurfaceControl.copy(
        padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
        minimumWidth = 9,
        minimumHeight = 12,
        cornerRadius = 0,
    ),
    /** 公开 `PixelComponentTokens` 的 `menu` 配置或运行值。
 *
 * Menu surface and item tokens.
 */
    public val menu: PixelComponentColorTokens = PixelComponentColorTokens.SurfaceControl.copy(
        elevationRole = PixelElevationRole.Low,
    ),
    /** 公开 `PixelComponentTokens` 的 `dropdown` 配置或运行值。
 *
 * Dropdown anchor and menu tokens.
 */
    public val dropdown: PixelComponentColorTokens = PixelComponentColorTokens.SurfaceControl.copy(
        elevationRole = PixelElevationRole.Low,
    ),
    /** 公开 `PixelComponentTokens` 的 `slidable` 配置或运行值。
 *
 * Slidable action tokens.
 */
    public val slidable: PixelComponentColorTokens = PixelComponentColorTokens.SurfaceControl,
    /** 公开 `PixelComponentTokens` 的 `dialog` 配置或运行值。
 *
 * Dialog surface tokens.
 */
    public val dialog: PixelComponentColorTokens = PixelComponentColorTokens.Overlay.copy(
        elevationRole = PixelElevationRole.High,
    ),
    /** 公开 `PixelComponentTokens` 的 `bottomSheet` 配置或运行值。
 *
 * Bottom-sheet surface tokens.
 */
    public val bottomSheet: PixelComponentColorTokens = PixelComponentColorTokens.Overlay.copy(
        elevationRole = PixelElevationRole.High,
    ),
    /** 公开 `PixelComponentTokens` 的 `toast` 配置或运行值。
 *
 * Toast notification tokens.
 */
    public val toast: PixelComponentColorTokens = PixelComponentColorTokens.Notification,
    /** 公开 `PixelComponentTokens` 的 `snackbar` 配置或运行值。
 *
 * Snackbar notification tokens.
 */
    public val snackbar: PixelComponentColorTokens = PixelComponentColorTokens.Notification,
    /** 公开 `PixelComponentTokens` 的 `tooltip` 配置或运行值。
 *
 * Tooltip surface tokens.
 */
    public val tooltip: PixelComponentColorTokens = PixelComponentColorTokens.Overlay.copy(
        padding = EdgeInsets.all(1),
    ),
    /** 公开 `PixelComponentTokens` 的 `progress` 配置或运行值。
 *
 * Progress indicator tokens.
 */
    public val progress: PixelComponentColorTokens = PixelComponentColorTokens.TrackControl.copy(
        focusIndicator = null,
    ),
    /** 公开 `PixelComponentTokens` 的 `refresh` 配置或运行值。
 *
 * Pull-to-refresh indicator tokens.
 */
    public val refresh: PixelComponentColorTokens = PixelComponentColorTokens.TrackControl,
    /** 公开 `PixelComponentTokens` 的 `scrollbar` 配置或运行值。
 *
 * Scrollbar tokens.
 */
    public val scrollbar: PixelComponentColorTokens = PixelComponentColorTokens.Scrollbar,
) {
    /** 集中提供 `PixelComponentTokens` 的 `<companion>` 共享入口。
 *
 * Namespace for the canonical role-based component token graph.
 */
    public companion object {
        /** 公开 `PixelComponentTokens` 的 `Default` 配置或运行值。
 *
 * Default role-based tokens shared by all theme presets.
 */
        public val Default: PixelComponentTokens = PixelComponentTokens()
    }
}

/** Creates a role property while preserving nullable no-paint values. */
private fun colorRoles(
    normal: PixelColorRole?,
    vararg overrides: Pair<PixelControlState, PixelColorRole?>,
): PixelStateProperty<PixelColorRole?> = PixelStateMap(normal, *overrides)

/** Maps standard literal spacing encodings to the active shared spacing scale. */
private fun resolveSpacingValue(value: Int, spacing: PixelSpacingTokens): Int {
    return when (value) {
        0 -> spacing.none
        1 -> spacing.extraSmall
        2 -> spacing.small
        4 -> spacing.medium
        8 -> spacing.large
        12 -> spacing.extraLarge
        else -> value
    }
}

/** Maps standard component-size encodings to the active shared size scale. */
private fun resolveComponentSizeValue(value: Int, sizes: PixelSizeTokens): Int {
    return when (value) {
        0 -> 0
        7 -> sizes.trackHeight
        8 -> sizes.iconSmall
        9 -> sizes.selectionControlExtent
        12 -> sizes.compactControlHeight
        14 -> sizes.switchWidth
        16 -> sizes.controlHeight
        24 -> sizes.touchTarget
        40 -> sizes.overlayMinimumWidth
        else -> value
    }
}

/** Validates a token that permits zero but never a negative logical pixel value. */
internal fun requireNonNegativeToken(name: String, value: Int) {
    require(value >= 0) { "$name must be >= 0, got $value" }
}

/** Validates a token that must occupy at least one logical pixel. */
internal fun requirePositiveToken(name: String, value: Int) {
    require(value > 0) { "$name must be > 0, got $value" }
}
