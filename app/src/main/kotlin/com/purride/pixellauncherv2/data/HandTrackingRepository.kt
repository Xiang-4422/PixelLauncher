package com.purride.pixellauncherv2.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.purride.pixellauncherv2.launcher.PixelMatterHandInputMapper
import com.purride.pixellauncherv2.launcher.PixelMatterHandSnapshot
import java.util.concurrent.ExecutorService

internal class HandTrackingRepository(
    private val context: Context,
    private val backgroundExecutor: ExecutorService,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    private val appContext = context.applicationContext
    private var cameraProvider: ProcessCameraProvider? = null
    private var handLandmarker: HandLandmarker? = null
    private var listener: ((PixelMatterHandSnapshot?) -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private var logicalWidth: Int = 1
    private var logicalHeight: Int = 1
    private var lastAnalyzeMs: Long = 0L
    private var lastSnapshot: PixelMatterHandSnapshot? = null
    private var lastDebugBitmap: Bitmap? = null
    private var debugListener: ((HandTrackingDebugFrame?) -> Unit)? = null
    private var running: Boolean = false
    private val staleRunnable = Runnable {
        lastSnapshot = null
        listener?.invoke(null)
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun start(
        lifecycleOwner: LifecycleOwner,
        logicalWidth: Int,
        logicalHeight: Int,
        listener: (PixelMatterHandSnapshot?) -> Unit,
        onDebugFrame: (HandTrackingDebugFrame?) -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        if (!hasCameraPermission()) {
            listener(null)
            onDebugFrame(null)
            return
        }
        this.logicalWidth = logicalWidth.coerceAtLeast(1)
        this.logicalHeight = logicalHeight.coerceAtLeast(1)
        this.listener = listener
        this.debugListener = onDebugFrame
        this.errorListener = onError
        if (running) return
        running = true
        lastAnalyzeMs = 0L
        backgroundExecutor.execute {
            setupHandLandmarker()
            mainHandler.post {
                if (running && handLandmarker != null) {
                    bindCamera(lifecycleOwner)
                }
            }
        }
    }

    fun stop() {
        running = false
        mainHandler.removeCallbacks(staleRunnable)
        listener?.invoke(null)
        debugListener?.invoke(null)
        listener = null
        debugListener = null
        errorListener = null
        lastSnapshot = null
        lastDebugBitmap = null
        unbindCamera()
        backgroundExecutor.execute {
            handLandmarker?.close()
            handLandmarker = null
        }
    }

    private fun setupHandLandmarker() {
        if (handLandmarker != null) return
        val baseOptions = BaseOptions.builder()
            .setDelegate(Delegate.CPU)
            .setModelAssetPath(modelAssetPath)
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(minHandDetectionConfidence)
            .setMinTrackingConfidence(minHandTrackingConfidence)
            .setMinHandPresenceConfidence(minHandPresenceConfidence)
            .setNumHands(maxHands)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(::onLandmarkerResult)
            .setErrorListener(::onLandmarkerError)
            .build()
        try {
            handLandmarker = HandLandmarker.createFromOptions(appContext, options)
        } catch (error: RuntimeException) {
            running = false
            Log.e(logTag, "Failed to initialize hand landmarker", error)
            mainHandler.post {
                errorListener?.invoke("CAMERA")
                listener?.invoke(null)
            }
        }
    }

    private fun bindCamera(lifecycleOwner: LifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener(
            {
                if (!running) return@addListener
                val provider = providerFuture.get()
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(backgroundExecutor, ::analyzeImage)
                    }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, analysis)
                } catch (error: RuntimeException) {
                    running = false
                    Log.e(logTag, "Failed to bind hand tracking camera", error)
                    errorListener?.invoke("CAMERA")
                    listener?.invoke(null)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun unbindCamera() {
        val action = {
            cameraProvider?.unbindAll()
            cameraProvider = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        val frameTimeMs = SystemClock.uptimeMillis()
        if (!running || handLandmarker == null || frameTimeMs - lastAnalyzeMs < frameIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalyzeMs = frameTimeMs
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val sourceWidth = imageProxy.width
            val sourceHeight = imageProxy.height
            val bitmapBuffer = createBitmap(
                sourceWidth,
                sourceHeight,
                Bitmap.Config.ARGB_8888,
            )
            imageProxy.use { proxy ->
                bitmapBuffer.copyPixelsFromBuffer(proxy.planes[0].buffer)
            }
            val transform = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                postScale(-1f, 1f, sourceWidth.toFloat(), sourceHeight.toFloat())
            }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer,
                0,
                0,
                bitmapBuffer.width,
                bitmapBuffer.height,
                transform,
                true,
            )
            lastDebugBitmap = scaledDebugBitmap(rotatedBitmap)
            val image = BitmapImageBuilder(rotatedBitmap).build()
            handLandmarker?.detectAsync(image, frameTimeMs)
        } catch (error: RuntimeException) {
            Log.e(logTag, "Failed to analyze hand tracking frame", error)
            imageProxy.close()
        }
    }

    private fun onLandmarkerResult(
        result: HandLandmarkerResult,
        input: MPImage,
    ) {
        if (!running) return
        val landmarks = result.landmarks().firstOrNull()
        if (landmarks == null || landmarks.size < landmarkCount) {
            mainHandler.post {
                emitSnapshot(null)
                emitDebugFrame(null, null, "NO HAND", 0f)
            }
            return
        }
        val normalizedX = FloatArray(landmarkCount)
        val normalizedY = FloatArray(landmarkCount)
        for (index in 0 until landmarkCount) {
            val landmark = landmarks[index]
            normalizedX[index] = landmark.x()
            normalizedY[index] = landmark.y()
        }
        val confidence = result.handednesses()
            .firstOrNull()
            ?.firstOrNull()
            ?.score()
            ?: 0f
        val snapshot = PixelMatterHandInputMapper.fromNormalizedLandmarks(
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            timestampMs = result.timestampMs(),
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            confidence = confidence,
            previous = lastSnapshot,
            mirrorX = false,
        )
        mainHandler.post { emitSnapshot(snapshot) }
        mainHandler.post {
            emitDebugFrame(
                normalizedX = snapshot?.let { normalizedX },
                normalizedY = snapshot?.let { normalizedY },
                label = snapshot?.gesture?.label ?: "NO HAND",
                confidence = confidence,
            )
        }
    }

    private fun emitSnapshot(snapshot: PixelMatterHandSnapshot?) {
        if (!running) return
        lastSnapshot = snapshot
        listener?.invoke(snapshot)
        mainHandler.removeCallbacks(staleRunnable)
        if (snapshot != null) {
            mainHandler.postDelayed(staleRunnable, PixelMatterHandSnapshot.staleTimeoutMs)
        }
    }

    private fun onLandmarkerError(error: RuntimeException) {
        Log.e(logTag, "Hand landmarker error", error)
        mainHandler.post {
            errorListener?.invoke("CAMERA")
            emitSnapshot(null)
            debugListener?.invoke(null)
        }
    }

    private fun emitDebugFrame(
        normalizedX: FloatArray?,
        normalizedY: FloatArray?,
        label: String,
        confidence: Float,
    ) {
        val bitmap = lastDebugBitmap ?: return
        debugListener?.invoke(
            HandTrackingDebugFrame(
                bitmap = bitmap,
                normalizedX = normalizedX?.copyOf(),
                normalizedY = normalizedY?.copyOf(),
                label = label,
                confidence = confidence,
            ),
        )
    }

    private fun scaledDebugBitmap(source: Bitmap): Bitmap {
        val targetWidth = debugFrameWidthPx.coerceAtMost(source.width.coerceAtLeast(1))
        val targetHeight = (source.height * (targetWidth.toFloat() / source.width.coerceAtLeast(1).toFloat()))
            .toInt()
            .coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, false)
    }

    private companion object {
        const val logTag: String = "HandTrackingRepository"
        const val modelAssetPath: String = "hand_landmarker.task"
        const val maxHands: Int = 1
        const val landmarkCount: Int = 21
        const val frameIntervalMs: Long = 66L
        const val debugFrameWidthPx: Int = 180
        const val minHandDetectionConfidence: Float = 0.5f
        const val minHandTrackingConfidence: Float = 0.5f
        const val minHandPresenceConfidence: Float = 0.5f
    }
}
