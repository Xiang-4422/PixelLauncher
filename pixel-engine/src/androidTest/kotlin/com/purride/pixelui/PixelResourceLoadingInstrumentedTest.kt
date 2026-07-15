package com.purride.pixelcore

import android.graphics.Bitmap
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 在真实 Android Looper 与 BitmapFactory 上验收资源线程和解码安全边界。 */
@RunWith(AndroidJUnit4::class)
class PixelResourceLoadingInstrumentedTest {
    /** 每个测试独占的后台资源线程。 */
    private lateinit var executor: ExecutorService

    /** 创建不会泄漏到其他测试的后台 executor。 */
    @Before
    fun setUp() {
        executor = Executors.newSingleThreadExecutor()
    }

    /** 停止后台 executor，避免 instrumentation 进程残留线程。 */
    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    /** 同步资源加载必须在 Android 主线程执行任何 IO 前失败。 */
    @Test
    fun synchronousLoadRejectsRealAndroidMainThread() {
        /** 实际 loader 调用次数。 */
        val loads = AtomicInteger()
        /** 主线程捕获的异常。 */
        val failure = AtomicReference<Throwable>()
        /** 使用真实 Looper 探针的正式加载器。 */
        val loader = PixelResourceLoader(PixelResourceCache(), executor)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                loader.loadBitmap("main-thread") {
                    loads.incrementAndGet()
                    pixelBitmap()
                }
            } catch (error: Throwable) {
                failure.set(error)
            }
        }

        assertTrue(failure.get() is IllegalStateException)
        assertTrue(failure.get().message.orEmpty().contains("main thread"))
        assertEquals(0, loads.get())
    }

    /** 主线程发起异步加载后，解析必须在调用方后台 executor 上执行。 */
    @Test
    fun asynchronousLoadRunsOffAndroidMainThread() {
        /** 主线程创建的异步句柄。 */
        val handle = AtomicReference<PixelResourceLoadHandle<PixelBitmap>>()
        /** loader 是否观察到 Android 主 Looper。 */
        val ranOnMainThread = AtomicReference<Boolean>()
        /** 使用真实 Looper 探针的正式加载器。 */
        val loader = PixelResourceLoader(PixelResourceCache(), executor)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            handle.set(
                loader.loadBitmapAsync("background") {
                    ranOnMainThread.set(Looper.myLooper() === Looper.getMainLooper())
                    pixelBitmap()
                },
            )
        }

        /** 在 instrumentation 测试线程等待的结果。 */
        val result = handle.get().await()
        assertFalse(ranOnMainThread.get())
        assertEquals(0xFF336699.toInt(), result.pixelAt(0, 0))
    }

    /** 真实 PNG 必须通过 magic/checksum/bounds 两阶段校验并保留像素。 */
    @Test
    fun pngDecodeValidatesChecksumAndProducesImmutablePixels() {
        /** 由 Android Bitmap 编码的确定性 2x2 PNG。 */
        val png = encodedPng(width = 2, height = 2)
        /** 当前 PNG 的正确 SHA-256。 */
        val digest = png.sha256Hex()
        png.requireSha256(digest, "instrumented PNG")

        /** 经过 bounds 预检和正式解码的结果。 */
        val decoded = decodeBitmapBytes(
            bytes = png,
            label = "instrumented PNG",
            limits = PixelBitmapDecodeLimits(maxEncodedBytes = png.size, maxDimension = 2, maxPixels = 4),
        )

        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
        assertEquals(0xFF336699.toInt(), decoded.pixelAt(0, 0))
        /** 修改 getter 副本不得影响解码结果。 */
        val exposed = decoded.pixels
        exposed[0] = 0
        assertEquals(0xFF336699.toInt(), decoded.pixelAt(0, 0))
    }

    /** 损坏 magic、编码长度和解码尺寸必须在安全边界处失败。 */
    @Test
    fun bitmapDecodeRejectsCorruptMagicAndConfiguredLimits() {
        /** 合法的 2x2 PNG。 */
        val png = encodedPng(width = 2, height = 2)
        /** 不支持编码头的错误。 */
        val corrupt = expectThrows<IllegalArgumentException> {
            decodeBitmapBytes(
                bytes = "not-an-image".toByteArray(),
                label = "corrupt",
                limits = PixelBitmapDecodeLimits(),
            )
        }
        /** 编码字节预算不足的错误。 */
        val encodedLimit = expectThrows<IllegalArgumentException> {
            decodeBitmapBytes(
                bytes = png,
                label = "encoded-limit",
                limits = PixelBitmapDecodeLimits(maxEncodedBytes = png.size - 1),
            )
        }
        /** 解码尺寸预算不足的错误。 */
        val dimensionLimit = expectThrows<IllegalArgumentException> {
            decodeBitmapBytes(
                bytes = png,
                label = "dimension-limit",
                limits = PixelBitmapDecodeLimits(maxDimension = 1),
            )
        }

        assertTrue(corrupt.message.orEmpty().contains("corrupt magic"))
        assertTrue(encodedLimit.message.orEmpty().contains("bytes"))
        assertTrue(dimensionLimit.message.orEmpty().contains("width 2"))
    }

    /** 构造单像素 SDK bitmap。 */
    private fun pixelBitmap(): PixelBitmap {
        return PixelBitmap(width = 1, height = 1, pixels = intArrayOf(0xFF336699.toInt()))
    }

    /** 使用 Android 编码器生成测试 PNG。 */
    private fun encodedPng(width: Int, height: Int): ByteArray {
        /** 原生 Android bitmap。 */
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF336699.toInt())
        /** 接收 PNG 编码字节的内存流。 */
        val output = ByteArrayOutputStream()
        try {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            /** 非空 PNG 结果。 */
            val bytes = output.toByteArray()
            assertNotNull(bytes)
            return bytes
        } finally {
            bitmap.recycle()
        }
    }

    /** 执行代码并返回指定类型异常。 */
    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
        return try {
            block()
            error("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error is T) error else throw error
        }
    }
}
