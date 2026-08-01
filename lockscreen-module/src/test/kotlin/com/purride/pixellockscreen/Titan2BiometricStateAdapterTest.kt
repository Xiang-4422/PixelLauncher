package com.purride.pixellockscreen

import com.purride.pixellockscreen.ui.LockscreenBiometricModality
import com.purride.pixellockscreen.ui.LockscreenBiometricPhase
import org.junit.Assert.assertEquals
import org.junit.Test

/** Titan 2 原生生物识别状态归并优先级测试。 */
class Titan2BiometricStateAdapterTest {
    /** 未注册传感器时不得伪造生物识别入口。 */
    @Test
    fun noEnrollmentRemainsUnavailable() {
        /** 当前无注册方式的状态。 */
        val state = resolveTitan2BiometricState(input())
        assertEquals(LockscreenBiometricModality.NONE, state.modality)
        assertEquals(LockscreenBiometricPhase.UNAVAILABLE, state.phase)
    }

    /** StrongAuth 禁止所有已注册方式时必须优先要求设备凭据。 */
    @Test
    fun strongAuthOverridesRunningAndMessages() {
        /** 当前重启后仍残留原生监听和消息的状态。 */
        val state = resolveTitan2BiometricState(
            input(
                fingerprintEnrolled = true,
                fingerprintRunning = true,
                fingerprintAllowed = false,
                messageText = "NOT RECOGNIZED",
            ),
        )
        assertEquals(LockscreenBiometricPhase.STRONG_AUTH_REQUIRED, state.phase)
        assertEquals("", state.messageText)
    }

    /** 可用传感器监听应优先于另一种方式的局部锁定。 */
    @Test
    fun availableRunningModalityOverridesPartialLockout() {
        /** 人脸锁定但指纹继续监听的组合状态。 */
        val state = resolveTitan2BiometricState(
            input(
                fingerprintEnrolled = true,
                faceEnrolled = true,
                fingerprintRunning = true,
                faceLockedOut = true,
            ),
        )
        assertEquals(LockscreenBiometricModality.FACE_AND_FINGERPRINT, state.modality)
        assertEquals(LockscreenBiometricPhase.SCANNING, state.phase)
    }

    /** 全部方式锁定和系统消息应映射为不同可见阶段。 */
    @Test
    fun lockoutAndErrorRemainDistinct() {
        assertEquals(
            LockscreenBiometricPhase.LOCKED_OUT,
            resolveTitan2BiometricState(
                input(fingerprintEnrolled = true, fingerprintLockedOut = true),
            ).phase,
        )
        /** 当前普通识别失败状态。 */
        val errorState = resolveTitan2BiometricState(
            input(fingerprintEnrolled = true, messageText = "NOT RECOGNIZED"),
        )
        assertEquals(LockscreenBiometricPhase.ERROR, errorState.phase)
        assertEquals("NOT RECOGNIZED", errorState.messageText)
        assertEquals(
            LockscreenBiometricPhase.ERROR,
            resolveTitan2BiometricState(
                input(
                    fingerprintEnrolled = true,
                    fingerprintRunning = true,
                    messageText = "MOVE FINGER",
                ),
            ).phase,
        )
    }

    /** 原生成功状态必须覆盖监听、锁定和提示。 */
    @Test
    fun authenticatedStateHasHighestPriority() {
        assertEquals(
            LockscreenBiometricPhase.SUCCESS,
            resolveTitan2BiometricState(
                input(
                    fingerprintEnrolled = true,
                    fingerprintLockedOut = true,
                    fingerprintAllowed = false,
                    authenticated = true,
                    messageText = "IGNORED",
                ),
            ).phase,
        )
    }

    /** 多行和超长系统消息必须转换为稳定单行。 */
    @Test
    fun messageSanitizerProducesBoundedSingleLine() {
        /** 当前清理后的系统消息。 */
        val message = sanitizeBiometricMessage("A\nB", "  ${"X".repeat(200)}  ")
        assertEquals(160, message.length)
        assertEquals(false, '\n' in message)
        assertEquals(false, "  " in message)
    }

    /** 构造默认无传感器的只读输入。 */
    private fun input(
        fingerprintEnrolled: Boolean = false,
        faceEnrolled: Boolean = false,
        fingerprintRunning: Boolean = false,
        faceRunning: Boolean = false,
        fingerprintLockedOut: Boolean = false,
        faceLockedOut: Boolean = false,
        fingerprintAllowed: Boolean = true,
        faceAllowed: Boolean = true,
        authenticated: Boolean = false,
        messageText: String = "",
    ): Titan2BiometricSnapshotInput = Titan2BiometricSnapshotInput(
        fingerprintEnrolled = fingerprintEnrolled,
        faceEnrolled = faceEnrolled,
        fingerprintRunning = fingerprintRunning,
        faceRunning = faceRunning,
        fingerprintLockedOut = fingerprintLockedOut,
        faceLockedOut = faceLockedOut,
        fingerprintAllowed = fingerprintAllowed,
        faceAllowed = faceAllowed,
        authenticated = authenticated,
        messageText = messageText,
    )
}
