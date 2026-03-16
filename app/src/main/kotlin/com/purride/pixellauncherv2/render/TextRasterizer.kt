package com.purride.pixellauncherv2.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface

class TextRasterizer(
    private val narrowTypeface: Typeface? = null,
    private val wideTypeface: Typeface? = null,
) : GlyphSource {

    override fun findGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
        val isWideGlyph = !character.isAsciiPrintable()
        val logicalWidth = if (isWideGlyph) style.wideAdvanceWidth else style.narrowAdvanceWidth
        val textSizeRatio = if (isWideGlyph) style.wideTextSizeRatio else style.narrowTextSizeRatio
        val minimumSampleRatio = if (isWideGlyph) style.wideMinimumSampleRatio else style.narrowMinimumSampleRatio
        val fontWeight = if (isWideGlyph) style.wideFontWeight else style.narrowFontWeight
        val fontFamily = if (isWideGlyph) style.wideFontFamily else style.narrowFontFamily
        val sourceWidth = logicalWidth * style.oversampleFactor
        val sourceHeight = style.cellHeight * style.oversampleFactor
        val bitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sourceHeight * textSizeRatio
            typeface = when {
                isWideGlyph && wideTypeface != null -> Typeface.create(wideTypeface, fontWeight.toTypefaceStyle())
                !isWideGlyph && narrowTypeface != null -> Typeface.create(narrowTypeface, fontWeight.toTypefaceStyle())
                else -> Typeface.create(fontFamily.toTypeface(), fontWeight.toTypefaceStyle())
            }
            isFilterBitmap = false
            textAlign = Paint.Align.LEFT
        }

        val glyphText = character.toString()
        val fontMetrics = paint.fontMetrics
        val baseline = ((sourceHeight - (fontMetrics.bottom - fontMetrics.top)) / 2f) - fontMetrics.top
        val measuredWidth = paint.measureText(glyphText)
        val startX = ((sourceWidth - measuredWidth) / 2f).coerceAtLeast(0f)
        canvas.drawText(glyphText, startX, baseline, paint)

        val sampledPixels = ByteArray(logicalWidth * style.cellHeight)
        val minimumLitSamples = (style.oversampleFactor * style.oversampleFactor * minimumSampleRatio)
            .toInt()
            .coerceAtLeast(1)

        for (logicalY in 0 until style.cellHeight) {
            for (logicalX in 0 until logicalWidth) {
                var litSamples = 0
                for (sampleY in 0 until style.oversampleFactor) {
                    for (sampleX in 0 until style.oversampleFactor) {
                        val pixel = bitmap.getPixel(
                            (logicalX * style.oversampleFactor) + sampleX,
                            (logicalY * style.oversampleFactor) + sampleY,
                        )
                        if (Color.alpha(pixel) > 90) {
                            litSamples += 1
                        }
                    }
                }

                val index = (logicalY * logicalWidth) + logicalX
                if (litSamples >= minimumLitSamples) {
                    sampledPixels[index] = 1
                }
            }
        }

        bitmap.recycle()

        return GlyphBitmap(
            width = logicalWidth,
            height = style.cellHeight,
            pixels = sampledPixels,
            metrics = GlyphMetrics(
                advanceWidth = logicalWidth,
                baselineOffset = (baseline / style.oversampleFactor).toInt(),
                isWideGlyph = isWideGlyph,
            ),
        )
    }

    private fun Char.isAsciiPrintable(): Boolean = code in 32..126

    private fun PixelFontWeight.toTypefaceStyle(): Int {
        return when (this) {
            PixelFontWeight.NORMAL -> Typeface.NORMAL
            PixelFontWeight.BOLD -> Typeface.BOLD
        }
    }

    private fun PixelFontFamily.toTypeface(): Typeface {
        return when (this) {
            PixelFontFamily.DEFAULT -> Typeface.DEFAULT
            PixelFontFamily.MONOSPACE -> Typeface.MONOSPACE
        }
    }
}
