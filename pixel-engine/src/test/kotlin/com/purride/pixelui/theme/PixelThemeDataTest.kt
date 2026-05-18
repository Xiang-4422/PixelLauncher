package com.purride.pixelui.theme

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.ThemeData
import com.purride.pixelui.withTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 验证 [PixelThemeData] token 派生逻辑与 [withTokens] 助手的正确性。
 *
 * 核心逻辑：当 style 字段保持构造默认值时，resolveXxxStyle() 从 tokens 派生；
 * 当 style 字段被显式覆盖时，直接采用覆盖值，跳过 tokens 推导。
 */
class PixelThemeDataTest {

    private val defaultTheme: ThemeData = ThemeData()

    // ── resolveTextStyle ──────────────────────────────────────────────────

    @Test
    fun `default tokens drive resolveTextStyle tone`() {
        // tokens.textTone 默认 ON，resolveTextStyle() 应派生出 tone=ON
        val style = defaultTheme.resolveTextStyle()
        assertEquals(PixelTone.ON, style.tone)
    }

    @Test
    fun `withTokens textTone change propagates to resolveTextStyle`() {
        val theme = defaultTheme.withTokens { copy(textTone = PixelTone.ACCENT) }
        val style = theme.resolveTextStyle()
        assertEquals(PixelTone.ACCENT, style.tone)
    }

    // ── style override beats tokens ───────────────────────────────────────

    @Test
    fun `explicit textStyle overrides token derivation`() {
        // 传入一个与默认不同的 style 对象，应直接采用，而不走 tokens 派生
        val overrideStyle = PixelTextStyle(tone = PixelTone.OFF)
        val theme = defaultTheme.copy(textStyle = overrideStyle)

        // 即便把 tokens.textTone 改成 ACCENT，显式 style 也不受影响
        val themeWithChangedToken = theme.withTokens { copy(textTone = PixelTone.ACCENT) }
        assertEquals(PixelTone.OFF, themeWithChangedToken.resolveTextStyle().tone)
    }

    // ── resolveAccentButtonStyle borderTone ───────────────────────────────

    @Test
    fun `accentBorderTone in tokens drives resolveAccentButtonStyle borderTone`() {
        val theme = defaultTheme.withTokens { copy(accentBorderTone = PixelTone.ON) }
        val style = theme.resolveAccentButtonStyle()
        assertEquals(PixelTone.ON, style.borderTone)
    }

    // ── resolveDisabledButtonStyle ────────────────────────────────────────

    @Test
    fun `disabledBorderTone in tokens drives resolveDisabledButtonStyle borderTone`() {
        // 默认 disabled borderTone 是 ON；改成 ACCENT 后 resolve 结果也应跟着变
        val theme = defaultTheme.withTokens { copy(disabledBorderTone = PixelTone.ACCENT) }
        val style = theme.resolveDisabledButtonStyle()
        assertEquals(PixelTone.ACCENT, style.borderTone)
    }

    // ── withTokens 助手 ───────────────────────────────────────────────────

    @Test
    fun `withTokens only mutates tokens field, not style overrides`() {
        val overrideButton = PixelButtonStyle(
            fillTone = PixelTone.OFF,
            borderTone = PixelTone.ACCENT,
            textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
        )
        val themeWithOverride = defaultTheme.copy(buttonStyle = overrideButton)
        val afterWithTokens = themeWithOverride.withTokens { copy(textTone = PixelTone.OFF) }

        // tokens 改变了
        assertEquals(PixelTone.OFF, afterWithTokens.tokens.textTone)
        // 但显式 buttonStyle 没有变
        assertEquals(overrideButton, afterWithTokens.buttonStyle)
    }

    @Test
    fun `withTokens does not affect unrelated token fields`() {
        val theme = defaultTheme.withTokens { copy(textTone = PixelTone.ACCENT) }
        // 只改 textTone，accentBorderTone 应还是默认值 ACCENT
        assertEquals(PixelTone.ACCENT, theme.tokens.accentBorderTone)
        // textTone 改成了 ACCENT，与 accentBorderTone 默认值相同——但这里验证的是"其他 token 不变"
        assertNotEquals(PixelTone.OFF, theme.tokens.textTone)
    }
}
