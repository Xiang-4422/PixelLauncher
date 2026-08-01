package com.purride.pixellauncherv2.app

import com.purride.pixelcore.PixelShape
import com.purride.pixeldesign.ProductAppearance
import com.purride.pixeldesign.ProductAppearanceContract
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixeldesign.ProductThemeMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 验证 Launcher 对外外观协议的固定编码和只读 Manifest 边界。 */
class ProductAppearanceProviderContractTest {
    /** Cursor 列与值必须严格遵循共享协议顺序。 */
    @Test
    fun cursorRowUsesStableSharedContract() {
        /** 一组覆盖全部字段的非默认外观。 */
        val appearance = ProductAppearance(
            pixelShape = PixelShape.DIAMOND,
            dotSizePx = 16,
            pixelGapEnabled = true,
            themeFamily = ProductThemeFamily.ARCADE,
            themeMode = ProductThemeMode.AUTO,
        )

        assertArrayEquals(
            arrayOf<Any>(
                ProductAppearanceContract.schemaVersion,
                "DIAMOND",
                16,
                1,
                "arcade",
                "AUTO",
            ),
            ProductAppearanceProvider.appearanceRow(appearance),
        )
    }

    /** Provider 必须直接启动可读、禁止 URI 授权，并由实现显式拒绝全部写操作。 */
    @Test
    fun manifestAndSourceKeepProviderReadOnly() {
        /** App 主 Manifest 文本。 */
        val manifest = File("src/main/AndroidManifest.xml").readText()
        /** Provider 实现源码。 */
        val source = File(
            "src/main/kotlin/com/purride/pixellauncherv2/app/ProductAppearanceProvider.kt",
        ).readText()

        assertTrue(manifest.contains("android:name=\".app.ProductAppearanceProvider\""))
        assertTrue(manifest.contains("android:authorities=\"\${applicationId}.appearance\""))
        assertTrue(manifest.contains("android:directBootAware=\"true\""))
        assertTrue(manifest.contains("android:grantUriPermissions=\"false\""))
        assertTrue(source.contains("override fun insert"))
        assertTrue(source.contains("override fun update"))
        assertTrue(source.contains("override fun delete"))
        assertTrue(source.contains("throw SecurityException"))
    }
}
