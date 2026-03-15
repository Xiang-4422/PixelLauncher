package com.purride.pixellauncherv2.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

data class DeviceMotionSnapshot(
    val gravityX: Float = 0f,
    val gravityY: Float = 0f,
    val gravityZ: Float = 0f,
    val linearAccelX: Float = 0f,
    val linearAccelY: Float = 0f,
    val linearAccelZ: Float = 0f,
    val accelMagnitude: Float = staticGravityMagnitude,
    val timestampNanos: Long = 0L,
) {
    companion object {
        const val staticGravityMagnitude: Float = 9.81f
    }
}

class DeviceMotionRepository(
    context: Context,
) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val hasGravitySensor: Boolean = gravitySensor != null

    private var listener: SensorEventListener? = null
    private var lastGravitySensorX: Float = 0f
    private var lastGravitySensorY: Float = 0f
    private var lastGravitySensorZ: Float = 0f
    private var filteredGravityX: Float = 0f
    private var filteredGravityY: Float = 0f
    private var filteredGravityZ: Float = 0f
    private var isAccelGravityInitialized: Boolean = false
    private var lastAccelFilterTimestampNanos: Long = 0L
    private var lastGravityX: Float = 0f
    private var lastGravityY: Float = 0f
    private var lastGravityZ: Float = 0f
    private var lastRawAccelX: Float = 0f
    private var lastRawAccelY: Float = 0f
    private var lastRawAccelZ: Float = 0f
    private var hasRawAccelSample: Boolean = false
    private var lastLinearAccelX: Float = 0f
    private var lastLinearAccelY: Float = 0f
    private var lastLinearAccelZ: Float = 0f
    private var lastAccelMagnitude: Float = DeviceMotionSnapshot.staticGravityMagnitude
    private var lastTimestampNanos: Long = 0L

    fun start(onMotionChanged: (DeviceMotionSnapshot) -> Unit) {
        stop()

        val accelerometer = accelerometerSensor
        if (accelerometer == null) {
            onMotionChanged(
                DeviceMotionSnapshot(
                    gravityX = lastGravityX,
                    gravityY = lastGravityY,
                    gravityZ = lastGravityZ,
                    linearAccelX = lastLinearAccelX,
                    linearAccelY = lastLinearAccelY,
                    linearAccelZ = lastLinearAccelZ,
                    accelMagnitude = lastAccelMagnitude,
                    timestampNanos = lastTimestampNanos,
                ),
            )
            return
        }

        val eventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val sensorEvent = event ?: return
                when (sensorEvent.sensor.type) {
                    Sensor.TYPE_GRAVITY -> {
                        lastGravitySensorX = sensorEvent.values.getOrElse(0) { 0f }
                        lastGravitySensorY = sensorEvent.values.getOrElse(1) { 0f }
                        lastGravitySensorZ = sensorEvent.values.getOrElse(2) { 0f }
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        lastRawAccelX = sensorEvent.values.getOrElse(0) { 0f }
                        lastRawAccelY = sensorEvent.values.getOrElse(1) { 0f }
                        lastRawAccelZ = sensorEvent.values.getOrElse(2) { 0f }
                        hasRawAccelSample = true
                        lastAccelMagnitude = sqrt(
                            (lastRawAccelX * lastRawAccelX) +
                                (lastRawAccelY * lastRawAccelY) +
                                (lastRawAccelZ * lastRawAccelZ),
                        )
                        updateGravityFilterFromAccelerometer(sensorEvent.timestamp)
                    }
                }
                updateOutputGravity()
                updateLinearAcceleration()
                lastTimestampNanos = sensorEvent.timestamp
                onMotionChanged(
                    DeviceMotionSnapshot(
                        gravityX = lastGravityX,
                        gravityY = lastGravityY,
                        gravityZ = lastGravityZ,
                        linearAccelX = lastLinearAccelX,
                        linearAccelY = lastLinearAccelY,
                        linearAccelZ = lastLinearAccelZ,
                        accelMagnitude = lastAccelMagnitude,
                        timestampNanos = lastTimestampNanos,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        listener = eventListener
        gravitySensor?.let {
            sensorManager.registerListener(eventListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.registerListener(eventListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        val currentListener = listener ?: return
        sensorManager.unregisterListener(currentListener)
        listener = null
    }

    private fun updateLinearAcceleration() {
        if (!hasRawAccelSample) {
            lastLinearAccelX = 0f
            lastLinearAccelY = 0f
            lastLinearAccelZ = 0f
            return
        }
        lastLinearAccelX = lastRawAccelX - lastGravityX
        lastLinearAccelY = lastRawAccelY - lastGravityY
        lastLinearAccelZ = lastRawAccelZ - lastGravityZ
    }

    private fun updateGravityFilterFromAccelerometer(timestampNanos: Long) {
        if (!isAccelGravityInitialized) {
            filteredGravityX = lastRawAccelX
            filteredGravityY = lastRawAccelY
            filteredGravityZ = lastRawAccelZ
            isAccelGravityInitialized = true
            lastAccelFilterTimestampNanos = timestampNanos
            return
        }

        val deltaSeconds = when {
            timestampNanos <= 0L || lastAccelFilterTimestampNanos <= 0L -> fallbackDeltaSeconds
            timestampNanos <= lastAccelFilterTimestampNanos -> fallbackDeltaSeconds
            else -> ((timestampNanos - lastAccelFilterTimestampNanos).toDouble() / nanosPerSecond).toFloat()
                .coerceAtLeast(1e-4f)
        }
        val alpha = (deltaSeconds / (gravityLpfTauSeconds + deltaSeconds)).coerceIn(0f, 1f)
        filteredGravityX += alpha * (lastRawAccelX - filteredGravityX)
        filteredGravityY += alpha * (lastRawAccelY - filteredGravityY)
        filteredGravityZ += alpha * (lastRawAccelZ - filteredGravityZ)
        lastAccelFilterTimestampNanos = timestampNanos
    }

    private fun updateOutputGravity() {
        when {
            hasGravitySensor && isAccelGravityInitialized -> {
                lastGravityX = (lastGravitySensorX * gravitySensorBlendWeight) + (filteredGravityX * filteredAccelBlendWeight)
                lastGravityY = (lastGravitySensorY * gravitySensorBlendWeight) + (filteredGravityY * filteredAccelBlendWeight)
                lastGravityZ = (lastGravitySensorZ * gravitySensorBlendWeight) + (filteredGravityZ * filteredAccelBlendWeight)
            }

            hasGravitySensor -> {
                lastGravityX = lastGravitySensorX
                lastGravityY = lastGravitySensorY
                lastGravityZ = lastGravitySensorZ
            }

            isAccelGravityInitialized -> {
                lastGravityX = filteredGravityX
                lastGravityY = filteredGravityY
                lastGravityZ = filteredGravityZ
            }
        }
    }

    private companion object {
        const val gravityLpfTauSeconds: Float = 0.120f
        const val gravitySensorBlendWeight: Float = 0.7f
        const val filteredAccelBlendWeight: Float = 0.3f
        const val fallbackDeltaSeconds: Float = 1f / 60f
        const val nanosPerSecond: Double = 1_000_000_000.0
    }
}
