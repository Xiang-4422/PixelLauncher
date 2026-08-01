package com.purride.pixellockscreen

import com.purride.pixellockscreen.ui.LockscreenBiometricModality
import com.purride.pixellockscreen.ui.LockscreenBiometricPhase
import com.purride.pixellockscreen.ui.LockscreenSecurityNoticePhase
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

    /** 即使没有注册传感器，系统 StrongAuth 也必须保持可见。 */
    @Test
    fun strongAuthRemainsVisibleWithoutEnrollment() {
        /** 当前用户主动 Lockdown 后的状态。 */
        val state = resolveTitan2BiometricState(input(strongAuthFlags = 0x20))
        assertEquals(LockscreenBiometricModality.NONE, state.modality)
        assertEquals(LockscreenBiometricPhase.STRONG_AUTH_REQUIRED, state.phase)
        assertEquals("LOCKDOWN - USE CREDENTIAL", state.messageText)
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
                strongAuthFlags = 0x1,
                messageText = "NOT RECOGNIZED",
            ),
        )
        assertEquals(LockscreenBiometricPhase.STRONG_AUTH_REQUIRED, state.phase)
        assertEquals("DEVICE RESTARTED - USE CREDENTIAL", state.messageText)
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

    /** 信任错误必须覆盖同帧成功和 Extend Unlock 提示。 */
    @Test
    fun trustAgentErrorHasHighestVisibleNoticePriority() {
        /** 同时存在三类原生文字时解析出的单一提示。 */
        val state = resolveTitan2SecurityNotice(
            trustAgentError = "TRUST ERROR",
            trustGranted = "TRUSTED",
            persistentUnlock = "EXTEND UNLOCK",
        )
        assertEquals(LockscreenSecurityNoticePhase.TRUST_ERROR, state.phase)
        assertEquals("TRUST ERROR", state.messageText)
    }

    /** 信任授予、持续解锁和空状态必须分别保持原生语义。 */
    @Test
    fun trustNoticeResolverPreservesVisibleSystemMeaning() {
        assertEquals(
            LockscreenSecurityNoticePhase.TRUSTED,
            resolveTitan2SecurityNotice(null, "TRUSTED", "EXTEND UNLOCK").phase,
        )
        assertEquals(
            LockscreenSecurityNoticePhase.EXTENDED_UNLOCK,
            resolveTitan2SecurityNotice(null, null, "EXTEND UNLOCK").phase,
        )
        assertEquals(
            LockscreenSecurityNoticePhase.NONE,
            resolveTitan2SecurityNotice(null, "  ", null).phase,
        )
    }

    /** 信任文字必须折叠多行空白并限制到 UI 状态允许的长度。 */
    @Test
    fun trustNoticeSanitizerProducesBoundedSingleLine() {
        /** 当前清理后的信任代理文字。 */
        val message = sanitizeSecurityNoticeMessage("  A\nB  ${"X".repeat(200)}  ")
        assertEquals(160, message.length)
        assertEquals(false, '\n' in message)
        assertEquals(false, "  " in message)
    }

    /** StrongAuth 必须压制可能残留的信任授予文字。 */
    @Test
    fun strongAuthSuppressesStaleTrustNotice() {
        /** 当前必须使用设备凭据的生物识别状态。 */
        val biometric = resolveTitan2BiometricState(input(strongAuthFlags = 0x1))
        /** 当前模拟的残留信任提示。 */
        val notice = resolveTitan2SecurityNotice(null, "TRUSTED", null)
        /** 合并安全优先级后的完整可见状态。 */
        val snapshot = resolveTitan2VisibleSecuritySnapshot(biometric, notice)

        assertEquals(LockscreenBiometricPhase.STRONG_AUTH_REQUIRED, snapshot.biometric.phase)
        assertEquals(LockscreenSecurityNoticePhase.NONE, snapshot.securityNotice.phase)
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
        strongAuthFlags: Int = 0,
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
        strongAuthFlags = strongAuthFlags,
        messageText = messageText,
    )
}
