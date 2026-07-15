package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Locks the complete M5 semantic theme graph in a deterministic, reviewable text snapshot. */
class PixelThemeTokenSnapshotTest {
    /** Four presets and every component-state role must match the reviewed snapshot exactly. */
    @Test
    fun completeThemeGraphMatchesReviewedSnapshot() {
        /** Fresh deterministic snapshot generated from production token objects. */
        val actual = buildSnapshot()
        /** Build report retained even when the reviewed resource is absent or differs. */
        val report = File("build/reports/theme/theme-token-snapshot.txt")
        report.parentFile?.mkdirs()
        report.writeText(actual)
        /** Reviewed classpath resource committed beside other deterministic golden evidence. */
        val resource = requireNotNull(javaClass.getResource(SNAPSHOT_RESOURCE)) {
            "Missing reviewed snapshot; generated candidate: ${report.absolutePath}"
        }
        /** Canonical expected text with one trailing newline. */
        val expected = resource.readText().trimEnd() + "\n"

        assertEquals("Theme token snapshot differs; inspect ${report.absolutePath}", expected, actual)
    }

    /** Serializes presets, foundation values, motion, labels, and every component state role. */
    private fun buildSnapshot(): String {
        /** Ordered public presets whose exact ARGB values form the supported built-in modes. */
        val presets = listOf(
            "dark" to PixelThemeTokens.Dark,
            "light" to PixelThemeTokens.Light,
            "highContrastDark" to PixelThemeTokens.HighContrastDark,
            "highContrastLight" to PixelThemeTokens.HighContrastLight,
        )
        /** Ordered standard component inventory matching PixelComponentTokens constructor fields. */
        val components = PixelComponentTokens.Default.run {
            listOf(
                "button" to button,
                "textButton" to textButton,
                "textField" to textField,
                "listTile" to listTile,
                "checkbox" to checkbox,
                "switch" to switch,
                "slider" to slider,
                "tabs" to tabs,
                "segmented" to segmented,
                "valueAdjuster" to valueAdjuster,
                "menu" to menu,
                "dropdown" to dropdown,
                "slidable" to slidable,
                "dialog" to dialog,
                "bottomSheet" to bottomSheet,
                "toast" to toast,
                "snackbar" to snackbar,
                "tooltip" to tooltip,
                "progress" to progress,
                "refresh" to refresh,
                "scrollbar" to scrollbar,
            )
        }
        /** All canonical states serialized in declaration order. */
        val states = PixelControlState.entries
        return buildString {
            appendLine("schemaVersion=1")
            presets.forEach { (name, theme) -> appendPreset(name, theme) }
            appendFoundation(PixelThemeTokens.Default)
            appendMotion(PixelThemeTokens.Default.motion)
            appendLabels(PixelThemeTokens.Default.labels)
            components.forEach { (name, tokens) -> appendComponent(name, tokens, states) }
        }
    }

    /** Appends one built-in preset and every concrete semantic ARGB role. */
    private fun StringBuilder.appendPreset(name: String, theme: PixelThemeTokens) {
        appendLine("[preset.$name]")
        appendLine("brightness=${theme.brightness}")
        appendLine("contrast=${theme.contrast}")
        COLOR_READERS.forEach { (role, reader) ->
            appendLine("color.$role=${reader(theme.colors).hexArgb()}")
        }
    }

    /** Appends the default typography and integer foundation scales. */
    private fun StringBuilder.appendFoundation(theme: PixelThemeTokens) {
        /** Default typography tokens whose metrics are shared by standard text consumers. */
        val typography = theme.typography
        /** Default spacing scale. */
        val spacing = theme.spacing
        /** Default component and icon sizes. */
        val sizes = theme.sizes
        /** Default stair-step radius scale. */
        val radii = theme.radii
        /** Default integer border scale. */
        val borders = theme.borders
        /** Default hard-shadow elevation scale. */
        val elevations = theme.elevations
        appendLine("[foundation]")
        appendTypography("body", typography.body)
        appendTypography("label", typography.label)
        appendTypography("title", typography.title)
        appendTypography("caption", typography.caption)
        appendTypography("button", typography.button)
        appendTypography("input", typography.input)
        appendLine(
            "spacing=${listOf(spacing.none, spacing.extraSmall, spacing.small, spacing.medium, spacing.large, spacing.extraLarge).joinToString(",")}",
        )
        appendLine(
            "sizes=${listOf(sizes.iconSmall, sizes.iconMedium, sizes.iconLarge, sizes.selectionControlExtent, sizes.switchWidth, sizes.trackHeight, sizes.compactControlHeight, sizes.controlHeight, sizes.touchTarget, sizes.overlayMinimumWidth).joinToString(",")}",
        )
        appendLine("radii=${listOf(radii.none, radii.small, radii.medium, radii.large, radii.pill).joinToString(",")}")
        appendLine("borders=${listOf(borders.none, borders.thin, borders.thick, borders.focus).joinToString(",")}")
        appendLine("elevations=${listOf(elevations.none, elevations.low, elevations.medium, elevations.high).joinToString(",")}")
    }

    /** Appends one typography role without serializing rasterizer object identity. */
    private fun StringBuilder.appendTypography(name: String, token: PixelTypographyToken) {
        appendLine(
            "typography.$name=${token.colorRole},${token.lineSpacing},${token.letterSpacing}," +
                "${token.lineHeight ?: "auto"},${token.fontScale},rasterizer=${token.textRasterizer != null}",
        )
    }

    /** Appends default motion duration, delay, transition, role, and spring values. */
    private fun StringBuilder.appendMotion(motion: PixelMotionThemeData) {
        /** Ordered motion channels supported by the complete theme. */
        val channels = listOf(
            "feedback" to motion.feedback,
            "selection" to motion.selection,
            "slidableSettle" to motion.slidableSettle,
            "dialogEnter" to motion.dialogEnter,
            "dialogExit" to motion.dialogExit,
            "popoverEnter" to motion.popoverEnter,
            "popoverExit" to motion.popoverExit,
            "route" to motion.route,
        )
        appendLine("[motion]")
        channels.forEach { (name, spec) ->
            /** Optional spring serialized by numeric value rather than object formatting. */
            val spring = spec.spring?.let { value ->
                "${value.stiffness}/${value.dampingRatio}/${value.mass}"
            } ?: "none"
            appendLine(
                "$name=${spec.duration.inWholeMilliseconds},${spec.delay.inWholeMilliseconds}," +
                    "${spec.transition},${spec.role},spring=$spring",
            )
        }
    }

    /** Appends every localized standard label in its stable constructor order. */
    private fun StringBuilder.appendLabels(labels: PixelLabelTokens) {
        appendLine("[labels]")
        listOf(
            "confirm" to labels.confirm,
            "cancel" to labels.cancel,
            "dismiss" to labels.dismiss,
            "empty" to labels.empty,
            "error" to labels.error,
            "loading" to labels.loading,
            "button" to labels.button,
            "textButton" to labels.textButton,
            "textField" to labels.textField,
            "listTile" to labels.listTile,
            "checkbox" to labels.checkbox,
            "switch" to labels.switch,
            "slider" to labels.slider,
            "tabs" to labels.tabs,
            "segmentedControl" to labels.segmentedControl,
            "valueAdjuster" to labels.valueAdjuster,
            "decrease" to labels.decrease,
            "increase" to labels.increase,
            "menu" to labels.menu,
            "dropdown" to labels.dropdown,
            "dialog" to labels.dialog,
            "bottomSheet" to labels.bottomSheet,
            "toast" to labels.toast,
            "snackbar" to labels.snackbar,
            "tooltip" to labels.tooltip,
            "progress" to labels.progress,
            "refresh" to labels.refresh,
            "scrollbar" to labels.scrollbar,
            "slidable" to labels.slidable,
        ).forEach { (name, value) -> appendLine("$name=$value") }
    }

    /** Appends one component's raw geometry and semantic roles for every canonical state. */
    private fun StringBuilder.appendComponent(
        name: String,
        tokens: PixelComponentColorTokens,
        states: List<PixelControlState>,
    ) {
        /** Default foundations used to prove semantic geometry resolution in the snapshot. */
        val theme = PixelThemeTokens.Default
        appendLine("[component.$name]")
        appendLine(
            "geometry=padding:${tokens.resolvePadding(theme.spacing).compact()}," +
                "min:${tokens.resolveMinimumWidth(theme.sizes)}x${tokens.resolveMinimumHeight(theme.sizes)}," +
                "border:${tokens.resolveBorderWidth(theme.borders)}," +
                "radius:${tokens.resolveCornerRadius(theme.radii)}," +
                "elevation:${tokens.resolveElevation(theme.elevations)}",
        )
        states.forEach { state ->
            /** Immutable state set containing exactly this canonical state. */
            val stateSet = PixelControlStateSet.of(state)
            appendLine(
                "state.${state.name}=" +
                    "container:${tokens.containerColor.resolve(stateSet).roleName()}," +
                    "content:${tokens.contentColor.resolve(stateSet).roleName()}," +
                    "border:${tokens.borderColor.resolve(stateSet).roleName()}," +
                    "focus:${tokens.focusIndicatorFor(stateSet)?.let { "${it.colorRole}/${it.width}/${it.inset}" } ?: "none"}",
            )
        }
    }

    /** Formats one nullable semantic color role without implementation-specific toString output. */
    private fun PixelColorRole?.roleName(): String = this?.name ?: "none"

    /** Formats four integer insets in left/top/right/bottom order. */
    private fun EdgeInsets.compact(): String = "$left/$top/$right/$bottom"

    /** Formats one exact ARGB value as eight uppercase hexadecimal digits. */
    private fun PixelColor.hexArgb(): String {
        return argb.toUInt().toString(radix = 16).padStart(8, '0').uppercase()
    }

    private companion object {
        /** Reviewed classpath snapshot path. */
        const val SNAPSHOT_RESOURCE: String = "/theme/theme-token-snapshot.txt"

        /** Ordered semantic color readers shared by all four built-in preset sections. */
        val COLOR_READERS: List<Pair<String, (PixelColorScheme) -> PixelColor>> = listOf(
            "background" to PixelColorScheme::background,
            "onBackground" to PixelColorScheme::onBackground,
            "surface" to PixelColorScheme::surface,
            "onSurface" to PixelColorScheme::onSurface,
            "surfaceVariant" to PixelColorScheme::surfaceVariant,
            "onSurfaceVariant" to PixelColorScheme::onSurfaceVariant,
            "outline" to PixelColorScheme::outline,
            "outlineVariant" to PixelColorScheme::outlineVariant,
            "primary" to PixelColorScheme::primary,
            "onPrimary" to PixelColorScheme::onPrimary,
            "danger" to PixelColorScheme::danger,
            "onDanger" to PixelColorScheme::onDanger,
            "warning" to PixelColorScheme::warning,
            "onWarning" to PixelColorScheme::onWarning,
            "disabled" to PixelColorScheme::disabled,
            "onDisabled" to PixelColorScheme::onDisabled,
            "inactive" to PixelColorScheme::inactive,
            "track" to PixelColorScheme::track,
            "focus" to PixelColorScheme::focus,
            "selection" to PixelColorScheme::selection,
            "scrim" to PixelColorScheme::scrim,
            "shadow" to PixelColorScheme::shadow,
        )
    }
}
