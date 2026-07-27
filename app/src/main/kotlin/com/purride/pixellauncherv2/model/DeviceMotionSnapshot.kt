package com.purride.pixellauncherv2.model

/**
 * 设备运动传感器快照：重力、线性加速度及其在屏幕坐标系下的分量。
 */
data class DeviceMotionSnapshot(
    /** 设备坐标系下的重力分量 X。 */
    val gravityX: Float = 0f,
    /** 设备坐标系下的重力分量 Y。 */
    val gravityY: Float = 0f,
    /** 设备坐标系下的重力分量 Z。 */
    val gravityZ: Float = 0f,
    /** 设备坐标系下的线性加速度分量 X（已去除重力）。 */
    val linearAccelX: Float = 0f,
    /** 设备坐标系下的线性加速度分量 Y（已去除重力）。 */
    val linearAccelY: Float = 0f,
    /** 设备坐标系下的线性加速度分量 Z（已去除重力）。 */
    val linearAccelZ: Float = 0f,
    /** 换算到屏幕坐标系后的重力分量 X。 */
    val screenGravityX: Float = gravityX,
    /** 换算到屏幕坐标系后的重力分量 Y。 */
    val screenGravityY: Float = gravityY,
    /** 换算到屏幕坐标系后的线性加速度分量 X。 */
    val screenLinearAccelX: Float = linearAccelX,
    /** 换算到屏幕坐标系后的线性加速度分量 Y。 */
    val screenLinearAccelY: Float = linearAccelY,
    /** 加速度计原始读数的模长。 */
    val accelMagnitude: Float = staticGravityMagnitude,
    /** 采样时间戳（纳秒）。 */
    val timestampNanos: Long = 0L,
) {
    companion object {
        /** 静止状态下的标准重力加速度模长，作为 [accelMagnitude] 的默认基准值。 */
        const val staticGravityMagnitude: Float = 9.81f
    }
}
