package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelGridGeometry
import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.PixelViewportPolicy
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.ScreenProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 验证方形 gap 位图批处理与历史逐格 Canvas 结果逐像素一致。 */
@RunWith(AndroidJUnit4::class)
class PixelHostSquareGapBatchInstrumentedTest {
    /** 对比透明、半透明、多色及 viewport 边缘组合下的完整 Android bitmap。 */
    @Test
    fun squareGapBitmapBatchMatchesPerCellReference() {
        assertSquareGapFrameMatchesReference(
            viewWidth = FractionalViewWidth,
            viewHeight = FractionalViewHeight,
            buffer = benchmarkBuffer(),
        )
    }

    /** 验证整数 origin 下的位图批处理与逐线参考帧完全一致。 */
    @Test
    fun integerOriginBitmapBatchMatchesPerCellReference() {
        assertSquareGapFrameMatchesReference(
            viewWidth = IntegerViewWidth,
            viewHeight = IntegerViewHeight,
            buffer = benchmarkBuffer(),
        )
    }

    /** 验证非零左上偏移的稀疏内容在裁剪提交后仍落在原逻辑坐标。 */
    @Test
    fun integerOriginSparseOffsetBitmapMatchesPerCellReference() {
        assertSquareGapFrameMatchesReference(
            viewWidth = IntegerViewWidth,
            viewHeight = IntegerViewHeight,
            buffer = sparseOffsetBuffer(),
        )
    }

    /** 验证完全透明帧只保留熄灭点阵背景，不提交无效的活动位图。 */
    @Test
    fun integerOriginEmptyBitmapMatchesPerCellReference() {
        assertSquareGapFrameMatchesReference(
            viewWidth = IntegerViewWidth,
            viewHeight = IntegerViewHeight,
            buffer = PixelBuffer(BufferWidth, BufferHeight),
        )
    }

    /** 在指定物理 viewport 下逐像素比较生产提交与历史逐格参考算法。 */
    private fun assertSquareGapFrameMatchesReference(
        viewWidth: Int,
        viewHeight: Int,
        buffer: PixelBuffer,
    ) {
        /** Host 生命周期源要求创建、绘制和释放均位于 Android 主线程。 */
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            /** 使用应用上下文创建的离屏 Host，不依赖 Activity 或实体屏幕交互。 */
            val host = PixelHostView(ApplicationProvider.getApplicationContext())
            host.layout(0, 0, viewWidth, viewHeight)
            host.offPixelColor = OffPixelColor
            host.setPixelGapEnabled(true)
            host.setPixelGapRatio(1f)

            host.submitFrame(
                pixelBuffer = buffer,
                screenProfile = TestProfile,
                backgroundColor = BezelColor,
            )

            /** 生产方形批处理路径渲染出的完整物理帧。 */
            val actual = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
            /** 历史逐格 drawRect 算法独立渲染出的完整物理帧。 */
            val expected = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
            try {
                host.draw(Canvas(actual))
                /** 第二次绘制必须复用已经生成的点阵背景且保持 bezel 精确回退。 */
                host.draw(Canvas(actual))
                drawPerCellReference(
                    canvas = Canvas(expected),
                    buffer = buffer,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                )

                /** 生产帧的全部 ARGB 物理像素。 */
                val actualPixels = IntArray(viewWidth * viewHeight)
                /** 对照帧的全部 ARGB 物理像素。 */
                val expectedPixels = IntArray(viewWidth * viewHeight)
                actual.getPixels(actualPixels, 0, viewWidth, 0, 0, viewWidth, viewHeight)
                expected.getPixels(expectedPixels, 0, viewWidth, 0, 0, viewWidth, viewHeight)
                assertArrayEquals(expectedPixels, actualPixels)
            } finally {
                actual.recycle()
                expected.recycle()
            }
        }
    }

    /** 构造固定的多色逻辑缓冲，确保颜色切换不会影响最终合成。 */
    private fun benchmarkBuffer(): PixelBuffer {
        /** 与 [TestProfile] 逻辑尺寸一致的待提交缓冲。 */
        val buffer = PixelBuffer(BufferWidth, BufferHeight)
        buffer.setPixel(0, 0, PixelColor.fromRgb(0xE0, 0x20, 0x20))
        buffer.setPixel(2, 0, PixelColor.fromRgb(0x20, 0xD0, 0x40))
        buffer.setPixel(3, 0, PixelColor.fromRgb(0xE0, 0x20, 0x20))
        buffer.setPixel(0, 1, PixelColor(0x804060E0.toInt()), PixelBlendMode.Src)
        buffer.setPixel(1, 1, PixelColor.fromRgb(0xF0, 0xD0, 0x20))
        buffer.setPixel(3, 1, PixelColor.fromRgb(0x20, 0xD0, 0x40))
        buffer.setPixel(0, 2, PixelColor.White)
        buffer.setPixel(2, 2, PixelColor.fromRgb(0xE0, 0x20, 0x20))
        return buffer
    }

    /** 构造不接触任一逻辑边缘的稀疏缓冲，覆盖裁剪后的坐标平移和半透明合成。 */
    private fun sparseOffsetBuffer(): PixelBuffer {
        /** 仅在中部包含两个点亮像素的待提交缓冲。 */
        val buffer = PixelBuffer(BufferWidth, BufferHeight)
        buffer.setPixel(1, 1, PixelColor.fromRgb(0x32, 0xA0, 0xE0))
        buffer.setPixel(2, 1, PixelColor(0x8070D040.toInt()), PixelBlendMode.Src)
        return buffer
    }

    /** 使用优化前的逐格方形算法渲染独立对照帧。 */
    private fun drawPerCellReference(
        canvas: Canvas,
        buffer: PixelBuffer,
        viewWidth: Int,
        viewHeight: Int,
    ) {
        /** 与生产 Host 相同的整数 contain 几何。 */
        val geometry = requireNotNull(
            PixelGridGeometryResolver.resolve(
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                profile = TestProfile,
                viewportPolicy = PixelViewportPolicy(),
                pixelGapEnabled = true,
                pixelGapRatio = 1f,
            ),
        )
        /** 禁用抗锯齿和过滤的逐格参考画笔。 */
        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = false
            isFilterBitmap = false
        }
        canvas.drawColor(BezelColor.argb)
        drawLogicalCells(canvas, buffer, geometry, paint, drawOnlyLitPixels = false)
        drawLogicalCells(canvas, buffer, geometry, paint, drawOnlyLitPixels = true)
        drawBezelGrid(canvas, geometry, paint)
    }

    /** 绘制完整熄灭点阵背景，或仅绘制 buffer 中的点亮像素。 */
    private fun drawLogicalCells(
        canvas: Canvas,
        buffer: PixelBuffer,
        geometry: PixelGridGeometry,
        paint: Paint,
        drawOnlyLitPixels: Boolean,
    ) {
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                /** 当前逻辑像素的最终 ARGB。 */
                val argb = buffer.pixels[y * buffer.width + x]
                if (drawOnlyLitPixels && (argb ushr 24) == 0) continue
                paint.color = if (drawOnlyLitPixels) argb else OffPixelColor.argb
                canvas.drawRect(
                    geometry.originX + dotLeft(geometry, x),
                    geometry.originY + dotTop(geometry, y),
                    geometry.originX + dotRight(geometry, buffer.width, x),
                    geometry.originY + dotBottom(geometry, buffer.height, y),
                    paint,
                )
            }
        }
    }

    /** 按生产顺序覆盖内部横纵 gap，保留 viewport 最外侧像素边缘。 */
    private fun drawBezelGrid(canvas: Canvas, geometry: PixelGridGeometry, paint: Paint) {
        paint.color = BezelColor.argb
        /** 一条内部 gap 在物理画布上的完整宽度。 */
        val gapWidth = geometry.dotInset * 2f
        for (x in 1 until BufferWidth) {
            /** 当前竖向内部网格线的左边界。 */
            val left = geometry.originX + x * geometry.cellSize - geometry.dotInset
            canvas.drawRect(
                left,
                geometry.originY,
                left + gapWidth,
                geometry.originY + geometry.contentHeight,
                paint,
            )
        }
        for (y in 1 until BufferHeight) {
            /** 当前横向内部网格线的上边界。 */
            val top = geometry.originY + y * geometry.cellSize - geometry.dotInset
            canvas.drawRect(
                geometry.originX,
                top,
                geometry.originX + geometry.contentWidth,
                top + gapWidth,
                paint,
            )
        }
    }

    /** 返回当前列点阵矩形的局部左边界。 */
    private fun dotLeft(geometry: PixelGridGeometry, x: Int): Float =
        x * geometry.cellSize + if (x == 0) 0f else geometry.dotInset

    /** 返回当前行点阵矩形的局部上边界。 */
    private fun dotTop(geometry: PixelGridGeometry, y: Int): Float =
        y * geometry.cellSize + if (y == 0) 0f else geometry.dotInset

    /** 返回当前列点阵矩形的局部右边界。 */
    private fun dotRight(geometry: PixelGridGeometry, width: Int, x: Int): Float =
        (x + 1) * geometry.cellSize - if (x == width - 1) 0f else geometry.dotInset

    /** 返回当前行点阵矩形的局部下边界。 */
    private fun dotBottom(geometry: PixelGridGeometry, height: Int, y: Int): Float =
        (y + 1) * geometry.cellSize - if (y == height - 1) 0f else geometry.dotInset

    /** 离屏 viewport、逻辑缓冲和测试颜色的稳定配置。 */
    private companion object {
        /** 产生半像素 origin、覆盖 direct-Canvas fallback 的物理宽度。 */
        const val FractionalViewWidth: Int = 83

        /** 产生半像素 origin、覆盖 direct-Canvas fallback 的物理高度。 */
        const val FractionalViewHeight: Int = 61

        /** 产生整数 origin、覆盖方形位图批处理的物理宽度。 */
        const val IntegerViewWidth: Int = 82

        /** 产生整数 origin、覆盖方形位图批处理的物理高度。 */
        const val IntegerViewHeight: Int = 60

        /** 逻辑缓冲列数。 */
        const val BufferWidth: Int = 4

        /** 逻辑缓冲行数。 */
        const val BufferHeight: Int = 3

        /** 默认方形像素与整数 contain 映射组成的测试 profile。 */
        val TestProfile: ScreenProfile = ScreenProfile(
            logicalWidth = BufferWidth,
            logicalHeight = BufferHeight,
            dotSizePx = 4,
            pixelShape = PixelShape.SQUARE,
        )

        /** viewport 外框与内部 gap 使用的深色。 */
        val BezelColor: PixelColor = PixelColor.fromRgb(0x03, 0x05, 0x07)

        /** 透明逻辑像素下方可见的点阵底色。 */
        val OffPixelColor: PixelColor = PixelColor.fromRgb(0x19, 0x1B, 0x1D)
    }
}
