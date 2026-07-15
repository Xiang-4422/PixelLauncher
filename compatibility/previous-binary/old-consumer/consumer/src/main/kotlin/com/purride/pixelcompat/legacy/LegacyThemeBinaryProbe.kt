package com.purride.pixelcompat.legacy

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelTextButtonStyle
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeColors
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.Text

/**
 * Legacy Theme entry point compiled only against the reviewed frozen engine AAR.
 *
 * The current runner invokes this object reflectively so every constructor, getter, component,
 * and copy call below remains frozen consumer bytecode while the current engine supplies classes.
 */
public object LegacyThemeBinaryProbe {
    /** Exercises the complete legacy Theme data-class ABI and returns a stable primitive summary. */
    @JvmStatic
    public fun run(): String {
        /** Legacy background token occupying constructor position 1. */
        val background = PixelColor.fromRgb(1, 2, 3)
        /** Legacy surface token occupying constructor position 2. */
        val surface = PixelColor.fromRgb(4, 5, 6)
        /** Legacy text token occupying constructor position 3. */
        val text = PixelColor.fromRgb(7, 8, 9)
        /** Legacy muted-text token occupying constructor position 4. */
        val mutedText = PixelColor.fromRgb(10, 11, 12)
        /** Legacy border token occupying constructor position 5. */
        val border = PixelColor.fromRgb(13, 14, 15)
        /** Legacy accent token occupying constructor position 6. */
        val accent = PixelColor.fromRgb(16, 17, 18)
        /** Legacy danger token occupying constructor position 7. */
        val danger = PixelColor.fromRgb(19, 20, 21)
        /** Legacy warning token occupying constructor position 8. */
        val warning = PixelColor.fromRgb(22, 23, 24)
        /** Legacy disabled token occupying constructor position 9. */
        val disabled = PixelColor.fromRgb(25, 26, 27)
        /** Legacy inactive token occupying constructor position 10. */
        val inactive = PixelColor.fromRgb(28, 29, 30)
        /** Legacy track token occupying constructor position 11. */
        val track = PixelColor.fromRgb(31, 32, 33)
        /** Legacy focus token occupying constructor position 12. */
        val focus = PixelColor.fromRgb(34, 35, 36)
        /** Legacy selection token occupying constructor position 13. */
        val selection = PixelColor.fromRgb(37, 38, 39)
        /**
         * Palette built through the old 13-argument constructor descriptor rather than defaults.
         */
        val colors = PixelThemeColors(
            background = background,
            surface = surface,
            text = text,
            mutedText = mutedText,
            border = border,
            accent = accent,
            danger = danger,
            warning = warning,
            disabled = disabled,
            inactive = inactive,
            track = track,
            focus = focus,
            selection = selection,
        )
        /** Getter checks forcing all 13 legacy property accessors to link and execute. */
        val colorGettersMatch =
            colors.background == background &&
                colors.surface == surface &&
                colors.text == text &&
                colors.mutedText == mutedText &&
                colors.border == border &&
                colors.accent == accent &&
                colors.danger == danger &&
                colors.warning == warning &&
                colors.disabled == disabled &&
                colors.inactive == inactive &&
                colors.track == track &&
                colors.focus == focus &&
                colors.selection == selection
        /** Component checks forcing all 13 legacy data-class component methods to execute. */
        val colorComponentsMatch =
            colors.component1() == background &&
                colors.component2() == surface &&
                colors.component3() == text &&
                colors.component4() == mutedText &&
                colors.component5() == border &&
                colors.component6() == accent &&
                colors.component7() == danger &&
                colors.component8() == warning &&
                colors.component9() == disabled &&
                colors.component10() == inactive &&
                colors.component11() == track &&
                colors.component12() == focus &&
                colors.component13() == selection
        /** Full copy call targeting the old 13-value copy method descriptor. */
        val fullColorCopy = colors.copy(
            background = background,
            surface = surface,
            text = text,
            mutedText = mutedText,
            border = border,
            accent = accent,
            danger = danger,
            warning = warning,
            disabled = disabled,
            inactive = inactive,
            track = track,
            focus = focus,
            selection = selection,
        )
        /** Equality proof that the direct full-copy invocation retained every legacy value. */
        val fullColorCopyMatches = fullColorCopy == colors
        /** Replacement accent used to force the old synthetic copy-default call descriptor. */
        val replacementAccent = PixelColor.fromRgb(200, 150, 100)
        /** Partial copy compiled to the old copy-default method with its original bit mask. */
        val partialColorCopy = colors.copy(accent = replacementAccent)
        /** Result proof that copy-default replaced only the requested legacy component. */
        val partialColorCopyMatches =
            partialColorCopy.accent == replacementAccent &&
                partialColorCopy.background == background &&
                partialColorCopy.selection == selection

        /** Legacy text style occupying PixelThemeData constructor position 2. */
        val textStyle = PixelTextStyle.Default
        /** Legacy outlined-button style occupying PixelThemeData constructor position 3. */
        val buttonStyle = PixelButtonStyle.Default
        /** Legacy text-button style occupying PixelThemeData constructor position 4. */
        val textButtonStyle = PixelTextButtonStyle.Default
        /** Legacy text-field style occupying PixelThemeData constructor position 5. */
        val textFieldStyle = PixelTextFieldStyle.Default
        /** Theme data built through the old five-argument public constructor descriptor. */
        val data = PixelThemeData(
            colors = colors,
            textStyle = textStyle,
            buttonStyle = buttonStyle,
            textButtonStyle = textButtonStyle,
            textFieldStyle = textFieldStyle,
        )
        /** Getter checks forcing all five legacy PixelThemeData accessors to execute. */
        val dataGettersMatch =
            data.colors == colors &&
                data.textStyle == textStyle &&
                data.buttonStyle == buttonStyle &&
                data.textButtonStyle == textButtonStyle &&
                data.textFieldStyle == textFieldStyle
        /** Component checks forcing all five legacy PixelThemeData component methods to execute. */
        val dataComponentsMatch =
            data.component1() == colors &&
                data.component2() == textStyle &&
                data.component3() == buttonStyle &&
                data.component4() == textButtonStyle &&
                data.component5() == textFieldStyle
        /** Full copy call targeting the old five-value PixelThemeData copy descriptor. */
        val fullDataCopy = data.copy(
            colors = colors,
            textStyle = textStyle,
            buttonStyle = buttonStyle,
            textButtonStyle = textButtonStyle,
            textFieldStyle = textFieldStyle,
        )
        /** Equality proof that the direct PixelThemeData copy retained all legacy values. */
        val fullDataCopyMatches = fullDataCopy == data
        /** Partial copy compiled to the old PixelThemeData copy-default descriptor. */
        val partialDataCopy = data.copy(colors = partialColorCopy)
        /** Result proof that the data copy-default call retained all non-color style values. */
        val partialDataCopyMatches =
            partialDataCopy.colors == partialColorCopy &&
                partialDataCopy.textStyle == textStyle &&
                partialDataCopy.buttonStyle == buttonStyle &&
                partialDataCopy.textButtonStyle == textButtonStyle &&
                partialDataCopy.textFieldStyle == textFieldStyle

        /** Child widget supplied explicitly to the old three-argument PixelTheme constructor. */
        val child = Text("LEGACY THEME")
        /** Stable key supplied explicitly to the old three-argument PixelTheme constructor. */
        val key = "legacy-theme-key"
        /** Theme provider built through the exact old data, child, and key constructor descriptor. */
        val theme = PixelTheme(data = data, child = child, key = key)
        /** Getter proof covering PixelTheme data plus inherited child and key accessors. */
        val themeGettersMatch =
            theme.data == data &&
                theme.child === child &&
                theme.key == key

        return "colors=$colorGettersMatch,$colorComponentsMatch," +
            "$fullColorCopyMatches,$partialColorCopyMatches;" +
            "data=$dataGettersMatch,$dataComponentsMatch," +
            "$fullDataCopyMatches,$partialDataCopyMatches;" +
            "theme=$themeGettersMatch"
    }
}
