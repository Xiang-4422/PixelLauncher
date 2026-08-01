package com.purride.pixeldesign.font

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 验证真实 Android assets 的异步 indexed 字体准备链路。 */
@RunWith(AndroidJUnit4::class)
class ProductFontRepositoryInstrumentedTest {
    /** catalog 中全部设置 face 都应能从真实 APK assets 异步准备并完成基础排版。 */
    @Test
    fun prepareEverySettingsFaceLoadsNativeAndChromeResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val executor = Executors.newFixedThreadPool(2)
        val repository = ProductFontRepository(context, executor, Handler(Looper.getMainLooper()))
        try {
            val selections = ProductFontCatalog.fontFamilyOptions().flatMap { family ->
                ProductFontCatalog.widthModeOptions(family).flatMap { widthMode ->
                    ProductFontCatalog.fontSizeOptions(family, widthMode).map { size ->
                        ProductFontSelection(family, widthMode, size)
                    }
                }
            }
            assertTrue("catalog must expose settings-visible faces", selections.isNotEmpty())
            selections.forEach { selection ->
                val prepared = prepareBlocking(repository, selection)
                assertEquals(selection, prepared.selection)
                assertTrue(
                    "native face must measure text for $selection",
                    prepared.defaultRasterizer.measureText("ABC 012 中文 .,:!?") > 0,
                )
                assertTrue(
                    "chrome face must measure text for $selection",
                    prepared.typography.rasterizer(ProductTextRole.CHROME).measureText("STATUS") > 0,
                )
            }
        } finally {
            repository.dispose()
            executor.shutdownNow()
        }
    }

    /** Pix32 原生和 chrome face 应在后台完整准备，且不接受不存在的字号。 */
    @Test
    fun preparePix32LoadsExactFacesWithoutFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val executor = Executors.newFixedThreadPool(2)
        val repository = ProductFontRepository(context, executor, Handler(Looper.getMainLooper()))
        val selection = ProductFontSelection(
            family = ProductFontFamily.PIX32,
            widthMode = ProductFontWidthMode.MONOSPACED,
            size = ProductFontSize.PX_12,
        )
        val latch = CountDownLatch(1)
        var result: Result<PreparedProductFont>? = null
        try {
            repository.prepare(selection) { completed ->
                result = completed
                latch.countDown()
            }
            assertTrue("font preparation timed out", latch.await(15, TimeUnit.SECONDS))
            val prepared = requireNotNull(result).getOrThrow()
            assertEquals(selection, prepared.selection)
            assertTrue(prepared.defaultRasterizer.measureText("ABC 中文") > 0)
            assertTrue(prepared.typography.rasterizer(ProductTextRole.CHROME).measureText("ABC") > 0)
            runCatching { prepared.rasterizer(selection.copy(size = ProductFontSize.PX_11)) }
                .onSuccess { error("unsupported exact size must not resolve") }
        } finally {
            repository.dispose()
            executor.shutdownNow()
        }
    }

    /** 等待一次后台字体准备完成，并把异步失败保留为当前测试失败。 */
    private fun prepareBlocking(
        repository: ProductFontRepository,
        selection: ProductFontSelection,
    ): PreparedProductFont {
        val latch = CountDownLatch(1)
        var result: Result<PreparedProductFont>? = null
        repository.prepare(selection) { completed ->
            result = completed
            latch.countDown()
        }
        assertTrue("font preparation timed out for $selection", latch.await(30, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
