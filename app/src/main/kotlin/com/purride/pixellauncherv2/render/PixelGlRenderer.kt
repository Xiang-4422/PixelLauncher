package com.purride.pixellauncherv2.render

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

object PixelGlCodec {
    fun encodePixelValue(value: Byte): Byte {
        return when (value) {
            PixelBuffer.ACCENT -> 0xFF.toByte()
            PixelBuffer.ON -> 0x7F.toByte()
            else -> 0x00
        }
    }

    fun encodePixelValues(source: ByteArray, target: ByteBuffer) {
        target.clear()
        for (index in source.indices) {
            target.put(encodePixelValue(source[index]))
        }
        target.flip()
    }

    fun encodeIdleMask(source: ByteArray, target: ByteBuffer) {
        target.clear()
        target.put(source)
        target.flip()
    }

    fun logicalTextureCoordinate(
        cellX: Int,
        cellY: Int,
        logicalWidth: Int,
        logicalHeight: Int,
    ): Pair<Float, Float> {
        return Pair(
            (cellX + 0.5f) / logicalWidth.toFloat().coerceAtLeast(1f),
            (cellY + 0.5f) / logicalHeight.toFloat().coerceAtLeast(1f),
        )
    }

    fun isDotMaskHit(
        pixelShape: PixelShape,
        localX: Float,
        localY: Float,
        dotSize: Float,
    ): Boolean {
        return when (pixelShape) {
            PixelShape.SQUARE -> true
            PixelShape.CIRCLE -> {
                val radius = dotSize * 0.5f
                val center = dotSize * 0.5f
                val dx = localX - center
                val dy = localY - center
                (dx * dx) + (dy * dy) <= (radius * radius)
            }
            PixelShape.DIAMOND -> {
                val radius = dotSize * 0.5f
                val center = dotSize * 0.5f
                val dx = kotlin.math.abs(localX - center)
                val dy = kotlin.math.abs(localY - center)
                (dx + dy) <= radius
            }
        }
    }
}

class PixelGlRenderer(
    private val frameSwapBuffer: FrameSwapBuffer,
    private val idleMaskSwapBuffer: IdleMaskSwapBuffer,
) : GLSurfaceView.Renderer {
    var pixelGapEnabled: Boolean = true
        private set
    private var programHandle: Int = 0
    private var staticTextureHandle: Int = 0
    private var idleMaskTextureHandle: Int = 0
    private var aPositionHandle: Int = 0
    private var uStaticTexHandle: Int = 0
    private var uIdleMaskTexHandle: Int = 0
    private var uIdleMaskEnabledHandle: Int = 0
    private var uViewSizeHandle: Int = 0
    private var uLogicalSizeHandle: Int = 0
    private var uCellSizeHandle: Int = 0
    private var uOriginHandle: Int = 0
    private var uDotInsetHandle: Int = 0
    private var uDotSizeHandle: Int = 0
    private var uPixelShapeHandle: Int = 0
    private var uBackgroundColorHandle: Int = 0
    private var uOffColorHandle: Int = 0
    private var uOnColorHandle: Int = 0
    private var uAccentColorHandle: Int = 0
    private var staticUploadBuffer: ByteBuffer = ByteBuffer.allocateDirect(1)
    private var staticUploadCapacity: Int = 1
    private var idleMaskUploadBuffer: ByteBuffer = ByteBuffer.allocateDirect(1)
    private var idleMaskUploadCapacity: Int = 1
    private var staticTextureWidth: Int = 0
    private var staticTextureHeight: Int = 0
    private var idleMaskTextureWidth: Int = 0
    private var idleMaskTextureHeight: Int = 0
    private var lastRenderedSequence: Long = 0L
    private var lastIdleMaskSequence: Long = 0L
    private var currentFrame: FramePayload? = null
    private var currentIdleMask: IdleMaskFrame? = null
    private var surfaceWidth: Int = 1
    private var surfaceHeight: Int = 1
    private var staticTextureDirty: Boolean = true
    private var idleMaskTextureDirty: Boolean = true
    private var fallbackPalette: PixelPalette = PixelPalette.terminalGreen()
    private val reusableColor = FloatArray(4)

    private val fullScreenQuad: FloatBuffer = ByteBuffer
        .allocateDirect(FULL_SCREEN_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULL_SCREEN_COORDS)
            position(0)
        }

    fun setFallbackPalette(palette: PixelPalette) {
        fallbackPalette = palette
    }

    fun setPixelGapEnabled(enabled: Boolean) {
        pixelGapEnabled = enabled
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        programHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        RenderPerfLogger.mark("gl.surface", "created unpackAlignment=1")
        aPositionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
        uStaticTexHandle = GLES20.glGetUniformLocation(programHandle, "uStaticTex")
        uIdleMaskTexHandle = GLES20.glGetUniformLocation(programHandle, "uIdleMaskTex")
        uIdleMaskEnabledHandle = GLES20.glGetUniformLocation(programHandle, "uIdleMaskEnabled")
        uViewSizeHandle = GLES20.glGetUniformLocation(programHandle, "uViewSize")
        uLogicalSizeHandle = GLES20.glGetUniformLocation(programHandle, "uLogicalSize")
        uCellSizeHandle = GLES20.glGetUniformLocation(programHandle, "uCellSize")
        uOriginHandle = GLES20.glGetUniformLocation(programHandle, "uOrigin")
        uDotInsetHandle = GLES20.glGetUniformLocation(programHandle, "uDotInset")
        uDotSizeHandle = GLES20.glGetUniformLocation(programHandle, "uDotSize")
        uPixelShapeHandle = GLES20.glGetUniformLocation(programHandle, "uPixelShape")
        uBackgroundColorHandle = GLES20.glGetUniformLocation(programHandle, "uBackgroundColor")
        uOffColorHandle = GLES20.glGetUniformLocation(programHandle, "uOffColor")
        uOnColorHandle = GLES20.glGetUniformLocation(programHandle, "uOnColor")
        uAccentColorHandle = GLES20.glGetUniformLocation(programHandle, "uAccentColor")
        staticTextureHandle = createTexture()
        idleMaskTextureHandle = createTexture()
        staticTextureDirty = true
        idleMaskTextureDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        RenderPerfLogger.mark("gl.surface", "changed width=$surfaceWidth height=$surfaceHeight")
    }

    override fun onDrawFrame(gl: GL10?) {
        RenderPerfLogger.measure("gl.onDrawFrame.total") {
            val latestFrame = frameSwapBuffer.consumeLatest(lastRenderedSequence)
            if (latestFrame != null) {
                currentFrame = latestFrame
                lastRenderedSequence = latestFrame.sequence
                fallbackPalette = latestFrame.palette
            }
            val latestIdleMask = idleMaskSwapBuffer.consumeLatest(lastIdleMaskSequence)
            if (latestIdleMask != null) {
                currentIdleMask = latestIdleMask.frame
                lastIdleMaskSequence = latestIdleMask.sequence
            }

            val frame = currentFrame ?: frameSwapBuffer.latest()
            if (frame == null) {
                drawBackgroundOnly(fallbackPalette)
                return@measure
            }

            if (latestFrame != null || staticTextureDirty) {
                RenderPerfLogger.measure("gl.upload.staticTexture") {
                    uploadStaticTexture(frame)
                }
                staticTextureDirty = false
            }
            if (latestIdleMask != null && currentIdleMask != null) {
                RenderPerfLogger.measure("gl.upload.idleMaskTexture") {
                    uploadIdleMaskTexture(currentIdleMask!!)
                }
                idleMaskTextureDirty = false
            }
            RenderPerfLogger.measure("gl.draw.composite") {
                drawFrame(
                    frame = frame,
                    idleMask = currentIdleMask,
                )
            }
        }
    }

    private fun drawBackgroundOnly(palette: PixelPalette) {
        val background = palette.backgroundColor
        GLES20.glClearColor(
            Color.red(background) / 255f,
            Color.green(background) / 255f,
            Color.blue(background) / 255f,
            1f,
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun uploadStaticTexture(frame: FramePayload) {
        val width = frame.pixelBuffer.width.coerceAtLeast(1)
        val height = frame.pixelBuffer.height.coerceAtLeast(1)
        val capacity = width * height
        if (capacity > staticUploadCapacity) {
            staticUploadBuffer = ByteBuffer
                .allocateDirect(capacity)
                .order(ByteOrder.nativeOrder())
            staticUploadCapacity = capacity
        }
        PixelGlCodec.encodePixelValues(
            source = frame.pixelBuffer.pixels,
            target = staticUploadBuffer,
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, staticTextureHandle)
        if (staticTextureWidth != width || staticTextureHeight != height) {
            staticTextureWidth = width
            staticTextureHeight = height
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_LUMINANCE,
                width,
                height,
                0,
                GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE,
                staticUploadBuffer,
            )
        } else {
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                width,
                height,
                GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE,
                staticUploadBuffer,
            )
        }
    }

    private fun uploadIdleMaskTexture(frame: IdleMaskFrame) {
        val width = frame.width.coerceAtLeast(1)
        val height = frame.height.coerceAtLeast(1)
        val capacity = width * height
        if (capacity > idleMaskUploadCapacity) {
            idleMaskUploadBuffer = ByteBuffer
                .allocateDirect(capacity)
                .order(ByteOrder.nativeOrder())
            idleMaskUploadCapacity = capacity
        }
        PixelGlCodec.encodeIdleMask(
            source = frame.mask,
            target = idleMaskUploadBuffer,
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, idleMaskTextureHandle)
        if (idleMaskTextureWidth != width || idleMaskTextureHeight != height) {
            idleMaskTextureWidth = width
            idleMaskTextureHeight = height
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_LUMINANCE,
                width,
                height,
                0,
                GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE,
                idleMaskUploadBuffer,
            )
        } else {
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                width,
                height,
                GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE,
                idleMaskUploadBuffer,
            )
        }
    }

    private fun drawFrame(frame: FramePayload, idleMask: IdleMaskFrame?) {
        val profile = frame.screenProfile
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = surfaceWidth,
            viewHeight = surfaceHeight,
            profile = profile,
            pixelGapEnabled = pixelGapEnabled,
        ) ?: run {
            drawBackgroundOnly(frame.palette)
            return
        }
        val logicalWidth = profile.logicalWidth.toFloat().coerceAtLeast(1f)
        val logicalHeight = profile.logicalHeight.toFloat().coerceAtLeast(1f)
        val idleMaskEnabled = idleMask != null &&
            idleMask.width > 0 &&
            idleMask.height > 0

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(programHandle)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, staticTextureHandle)
        GLES20.glUniform1i(uStaticTexHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, idleMaskTextureHandle)
        GLES20.glUniform1i(uIdleMaskTexHandle, 1)
        GLES20.glUniform1i(uIdleMaskEnabledHandle, if (idleMaskEnabled) 1 else 0)
        GLES20.glUniform2f(uViewSizeHandle, surfaceWidth.toFloat(), surfaceHeight.toFloat())
        GLES20.glUniform2f(uLogicalSizeHandle, logicalWidth, logicalHeight)
        GLES20.glUniform1f(uCellSizeHandle, geometry.cellSize)
        GLES20.glUniform2f(uOriginHandle, geometry.originX, geometry.originY)
        GLES20.glUniform1f(uDotInsetHandle, geometry.dotInset)
        GLES20.glUniform1f(uDotSizeHandle, geometry.dotSize)
        GLES20.glUniform1i(uPixelShapeHandle, shapeIndex(profile.pixelShape))
        setUniformColor(uBackgroundColorHandle, frame.palette.backgroundColor)
        setUniformColor(uOffColorHandle, frame.palette.pixelOffColor)
        setUniformColor(uOnColorHandle, frame.palette.pixelOnColor)
        setUniformColor(uAccentColorHandle, frame.palette.accentColor)

        fullScreenQuad.position(0)
        GLES20.glVertexAttribPointer(
            aPositionHandle,
            POSITION_COMPONENTS,
            GLES20.GL_FLOAT,
            false,
            POSITION_STRIDE_BYTES,
            fullScreenQuad,
        )
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
        GLES20.glDisableVertexAttribArray(aPositionHandle)
    }

    private fun setUniformColor(location: Int, color: Int) {
        reusableColor[0] = Color.red(color) / 255f
        reusableColor[1] = Color.green(color) / 255f
        reusableColor[2] = Color.blue(color) / 255f
        reusableColor[3] = 1f
        GLES20.glUniform4fv(location, 1, reusableColor, 0)
    }

    private fun shapeIndex(shape: PixelShape): Int {
        return when (shape) {
            PixelShape.SQUARE -> 0
            PixelShape.CIRCLE -> 1
            PixelShape.DIAMOND -> 2
        }
    }

    private fun createTexture(): Int {
        val handles = IntArray(1)
        GLES20.glGenTextures(1, handles, 0)
        val texture = handles[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return texture
    }

    private fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val message = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("GL program link failed: $message")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES20.GL_TRUE) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("GL shader compile failed: $message")
        }
        return shader
    }

    private companion object {
        private const val POSITION_COMPONENTS = 2
        private const val POSITION_STRIDE_BYTES = POSITION_COMPONENTS * 4
        private const val VERTEX_COUNT = 4
        private val FULL_SCREEN_COORDS = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uStaticTex;
            uniform sampler2D uIdleMaskTex;
            uniform int uIdleMaskEnabled;
            uniform vec2 uViewSize;
            uniform vec2 uLogicalSize;
            uniform float uCellSize;
            uniform vec2 uOrigin;
            uniform float uDotInset;
            uniform float uDotSize;
            uniform int uPixelShape;
            uniform vec4 uBackgroundColor;
            uniform vec4 uOffColor;
            uniform vec4 uOnColor;
            uniform vec4 uAccentColor;

            bool isInsideDot(vec2 dotLocal) {
                if (uPixelShape == 0) {
                    return true;
                }
                vec2 center = vec2(uDotSize * 0.5);
                vec2 shifted = dotLocal - center;
                if (uPixelShape == 1) {
                    float radius = uDotSize * 0.5;
                    return dot(shifted, shifted) <= (radius * radius);
                }
                vec2 diamond = abs(shifted);
                return (diamond.x + diamond.y) <= (uDotSize * 0.5);
            }

            vec4 decodeStaticColor(float encoded) {
                if (encoded > 0.75) {
                    return uAccentColor;
                } else if (encoded > 0.25) {
                    return uOnColor;
                }
                return uOffColor;
            }

            void main() {
                vec2 fragTopLeft = vec2(
                    gl_FragCoord.x - 0.5,
                    uViewSize.y - gl_FragCoord.y - 0.5
                );
                vec2 local = fragTopLeft - uOrigin;
                vec2 contentSize = uLogicalSize * uCellSize;
                if (local.x < 0.0 || local.y < 0.0 || local.x >= contentSize.x || local.y >= contentSize.y) {
                    gl_FragColor = uBackgroundColor;
                    return;
                }

                vec2 cell = floor(local / uCellSize);
                vec2 inCell = local - (cell * uCellSize);
                vec2 dotMin = vec2(uDotInset);
                vec2 dotMax = dotMin + vec2(uDotSize);
                if (inCell.x < dotMin.x || inCell.y < dotMin.y || inCell.x >= dotMax.x || inCell.y >= dotMax.y) {
                    gl_FragColor = uBackgroundColor;
                    return;
                }
                vec2 dotLocal = inCell - dotMin;
                if (!isInsideDot(dotLocal)) {
                    gl_FragColor = uBackgroundColor;
                    return;
                }

                vec2 texCoord = vec2(
                    (cell.x + 0.5) / uLogicalSize.x,
                    (cell.y + 0.5) / uLogicalSize.y
                );
                float encoded = texture2D(uStaticTex, texCoord).r;
                vec4 color = decodeStaticColor(encoded);
                if (uIdleMaskEnabled == 1) {
                    float idleMask = texture2D(uIdleMaskTex, texCoord).r;
                    if (idleMask > 0.25 && encoded <= 0.25) {
                        color = uOnColor;
                    }
                }
                gl_FragColor = color;
            }
        """
    }
}
