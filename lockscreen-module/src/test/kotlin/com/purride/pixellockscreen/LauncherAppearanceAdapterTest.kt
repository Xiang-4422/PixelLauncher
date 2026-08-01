package com.purride.pixellockscreen

import com.purride.pixelcore.PixelShape
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixeldesign.ProductThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Launcher 跨进程外观行的纯解码测试。 */
class LauncherAppearanceAdapterTest {
    /** 完整有效记录必须无损恢复所有共享字段。 */
    @Test
    fun validRecordDecodesAllFields() {
        /** 一条来自 Debug 或 Release Provider 的合法记录。 */
        val appearance = decodeProductAppearanceRecord(
            ProductAppearanceRecord(
                schemaVersion = 1,
                pixelShape = "CIRCLE",
                dotSizePx = 16,
                pixelGapEnabled = 1,
                themeFamily = "bubblegum",
                themeMode = "AUTO",
            ),
        )

        requireNotNull(appearance)
        assertEquals(PixelShape.CIRCLE, appearance.pixelShape)
        assertEquals(16, appearance.dotSizePx)
        assertEquals(true, appearance.pixelGapEnabled)
        assertEquals(ProductThemeFamily.BUBBLEGUM, appearance.themeFamily)
        assertEquals(ProductThemeMode.AUTO, appearance.themeMode)
    }

    /** 同版本脏字段必须逐项回退共享默认值，不能让 SystemUI 崩溃。 */
    @Test
    fun invalidFieldsFallBackToSharedDefaults() {
        /** 字段内容损坏但协议版本仍兼容的记录。 */
        val appearance = decodeProductAppearanceRecord(
            ProductAppearanceRecord(1, "bad", 99, 3, "bad", "bad"),
        )

        requireNotNull(appearance)
        assertEquals(PixelShape.SQUARE, appearance.pixelShape)
        assertEquals(12, appearance.dotSizePx)
        assertEquals(false, appearance.pixelGapEnabled)
        assertEquals(ProductThemeFamily.MIDNIGHT, appearance.themeFamily)
        assertEquals(ProductThemeMode.NIGHT, appearance.themeMode)
    }

    /** 未知协议版本必须整行拒绝，避免误解码未来不兼容语义。 */
    @Test
    fun incompatibleSchemaIsRejected() {
        /** 模拟未来不兼容 Provider 返回的记录。 */
        val record = ProductAppearanceRecord(2, "SQUARE", 12, 0, "midnight", "NIGHT")

        assertNull(decodeProductAppearanceRecord(record))
    }
}
