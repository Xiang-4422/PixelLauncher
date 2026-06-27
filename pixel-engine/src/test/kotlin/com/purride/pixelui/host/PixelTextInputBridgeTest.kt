package com.purride.pixelui.host

import android.text.InputType
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.internal.host.resolveAndroidInputType
import com.purride.pixelui.internal.host.resolveAndroidImeOptions
import com.purride.pixelui.internal.host.normalizePrintableAsciiUppercase
import com.purride.pixelui.internal.host.shouldRestartAndroidTextInput
import com.purride.pixelui.internal.host.toAndroidEditorConfig
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 [resolveAndroidInputType] 把每个 [PixelInputType] 枚举值
 * 映射到正确的 Android `InputType` 位组合。
 *
 * [android.text.InputType] 常量在 JVM 单元测试的 Android stub jar 中
 * 可访问，无需真实设备或 Robolectric。
 */
class PixelTextInputBridgeTest {

    @Test
    fun `ASCII normalization uppercases and removes unsupported input`() {
        assertEquals("HELLO 123!", normalizePrintableAsciiUppercase("Hello 你123!"))
    }

    @Test
    fun `IME restart is limited to focus and editor configuration changes`() {
        val textConfig = com.purride.pixelui.PixelTextInputRequest(
            text = "A",
            inputType = PixelInputType.ASCII,
            action = PixelTextInputAction.SEARCH,
        ).toAndroidEditorConfig()
        val numberConfig = com.purride.pixelui.PixelTextInputRequest(
            text = "1",
            inputType = PixelInputType.NUMBER,
            action = PixelTextInputAction.DONE,
        ).toAndroidEditorConfig()
        val changedTextConfig = com.purride.pixelui.PixelTextInputRequest(
            text = "ABC",
            inputType = PixelInputType.ASCII,
            action = PixelTextInputAction.SEARCH,
        ).toAndroidEditorConfig()

        assertEquals(true, shouldRestartAndroidTextInput(false, textConfig, textConfig))
        assertEquals(false, shouldRestartAndroidTextInput(true, textConfig, changedTextConfig))
        assertEquals(true, shouldRestartAndroidTextInput(true, textConfig, numberConfig))
    }

    @Test
    fun `TEXT single-line maps to TYPE_CLASS_TEXT`() {
        assertEquals(
            InputType.TYPE_CLASS_TEXT,
            resolveAndroidInputType(PixelInputType.TEXT, multiLine = false),
        )
    }

    @Test
    fun `TEXT multi-line adds MULTI_LINE flag`() {
        assertEquals(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            resolveAndroidInputType(PixelInputType.TEXT, multiLine = true),
        )
    }

    @Test
    fun `ASCII maps to uppercase visible text input`() {
        assertEquals(
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
            resolveAndroidInputType(PixelInputType.ASCII, multiLine = false),
        )
    }

    @Test
    fun `ASCII requests an ASCII keyboard while preserving the action`() {
        assertEquals(
            EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_FORCE_ASCII,
            resolveAndroidImeOptions(PixelTextInputAction.SEARCH, PixelInputType.ASCII),
        )
        assertEquals(
            EditorInfo.IME_ACTION_SEARCH,
            resolveAndroidImeOptions(PixelTextInputAction.SEARCH, PixelInputType.TEXT),
        )
    }

    @Test
    fun `NUMBER maps to TYPE_CLASS_NUMBER with signed and decimal flags`() {
        assertEquals(
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_SIGNED or
                InputType.TYPE_NUMBER_FLAG_DECIMAL,
            resolveAndroidInputType(PixelInputType.NUMBER, multiLine = false),
        )
    }

    @Test
    fun `NUMBER_PASSWORD maps to TYPE_CLASS_NUMBER with password variation`() {
        assertEquals(
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            resolveAndroidInputType(PixelInputType.NUMBER_PASSWORD, multiLine = false),
        )
    }

    @Test
    fun `EMAIL maps to TYPE_CLASS_TEXT with email variation`() {
        assertEquals(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            resolveAndroidInputType(PixelInputType.EMAIL, multiLine = false),
        )
    }

    @Test
    fun `PHONE maps to TYPE_CLASS_PHONE`() {
        assertEquals(
            InputType.TYPE_CLASS_PHONE,
            resolveAndroidInputType(PixelInputType.PHONE, multiLine = false),
        )
    }

    @Test
    fun `URL maps to TYPE_CLASS_TEXT with URI variation`() {
        assertEquals(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            resolveAndroidInputType(PixelInputType.URL, multiLine = false),
        )
    }

    @Test
    fun `PASSWORD maps to TYPE_CLASS_TEXT with password variation`() {
        assertEquals(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            resolveAndroidInputType(PixelInputType.PASSWORD, multiLine = false),
        )
    }
}
