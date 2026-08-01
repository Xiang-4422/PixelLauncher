package com.purride.pixellockscreen.credential

import com.android.internal.widget.LockPatternUtils
import com.android.keyguard.FakeUnlockAttempt
import com.android.keyguard.KeyguardSecurityContainerController
import com.android.keyguard.KeyguardSecurityModel
import com.android.keyguard.RecordingKeyguardSecurityCallback
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证系统校验结果严格复用 Titan 2 原生上报与 dismiss 顺序。 */
class Titan2KeyguardSecurityBridgeTest {
    /** 三种普通设备凭据模式都必须能够建立精确绑定。 */
    @Test
    fun supportedCredentialModesBindExactly() {
        /** 原生模式到通用模式的预期映射。 */
        val expectedModes = mapOf(
            KeyguardSecurityModel.SecurityMode.Pattern to Titan2CredentialMode.PATTERN,
            KeyguardSecurityModel.SecurityMode.PIN to Titan2CredentialMode.PIN,
            KeyguardSecurityModel.SecurityMode.Password to Titan2CredentialMode.PASSWORD,
        )

        expectedModes.forEach { (nativeMode, expectedMode) ->
            /** 当前模式的测试装配。 */
            val fixture = Fixture(nativeMode)
            /** 当前模式解析出的安全桥。 */
            val bridge = fixture.bind()
            assertEquals(expectedMode, bridge.credentialMode)
            assertEquals(fixture.user.currentUserId, bridge.userId)
        }
    }

    /** SIM 等特殊模式不得误入通用设备凭据校验。 */
    @Test
    fun specialSecurityModeIsRejected() {
        /** 当前 SIM 模式测试装配。 */
        val fixture = Fixture(KeyguardSecurityModel.SecurityMode.SimPinPukMe1)
        assertThrows(IllegalStateException::class.java) { fixture.bind() }
    }

    /** 成功必须先上报，再请求当前用户与模式的原生 dismiss。 */
    @Test
    fun matchedCredentialUsesNativeSuccessAndDismissChain() {
        /** 当前图案模式测试装配。 */
        val fixture = Fixture(KeyguardSecurityModel.SecurityMode.Pattern)
        /** 已绑定的安全桥。 */
        val bridge = fixture.bind()

        assertEquals(
            KeyguardSecurityDisposition.DismissRequested,
            bridge.complete(CredentialCheckResult.Matched),
        )
        assertEquals(listOf(FakeUnlockAttempt(10, 0, true)), fixture.callback.attempts)
        assertEquals(
            listOf(10 to KeyguardSecurityModel.SecurityMode.Pattern),
            fixture.callback.dismissals,
        )
    }

    /** 普通失败只交给系统累计，不自行维护失败次数。 */
    @Test
    fun rejectedCredentialReportsNativeFailure() {
        /** 当前 PIN 模式测试装配。 */
        val fixture = Fixture(KeyguardSecurityModel.SecurityMode.PIN)
        /** 已绑定的安全桥。 */
        val bridge = fixture.bind()

        assertEquals(
            KeyguardSecurityDisposition.FailureReported,
            bridge.complete(CredentialCheckResult.Rejected),
        )
        assertEquals(listOf(FakeUnlockAttempt(10, 0, false)), fixture.callback.attempts)
        assertTrue(fixture.callback.dismissals.isEmpty())
    }

    /** 限流时间必须先上报，再由 LockPatternUtils 写入系统截止时间。 */
    @Test
    fun throttledCredentialUsesSystemLockoutDeadline() {
        /** 当前密码模式测试装配。 */
        val fixture = Fixture(KeyguardSecurityModel.SecurityMode.Password)
        /** 已绑定的安全桥。 */
        val bridge = fixture.bind()

        assertEquals(
            KeyguardSecurityDisposition.LockoutStarted(1_030_000L),
            bridge.complete(CredentialCheckResult.Throttled(30_000)),
        )
        assertEquals(listOf(FakeUnlockAttempt(10, 30_000, false)), fixture.callback.attempts)
        assertEquals(10, fixture.lockPatternUtils.deadlineUserId)
        assertEquals(30_000, fixture.lockPatternUtils.deadlineTimeoutMillis)
    }

    /** 取消、用户切换和模式变化都不能影响新的认证上下文。 */
    @Test
    fun cancelledOrStaleResultHasNoNativeSideEffect() {
        /** 当前图案模式测试装配。 */
        val fixture = Fixture(KeyguardSecurityModel.SecurityMode.Pattern)
        /** 已绑定的安全桥。 */
        val bridge = fixture.bind()

        assertEquals(KeyguardSecurityDisposition.Cancelled, bridge.complete(CredentialCheckResult.Cancelled))
        fixture.user.currentUserId = 11
        assertEquals(KeyguardSecurityDisposition.StaleContext, bridge.complete(CredentialCheckResult.Matched))
        fixture.user.currentUserId = 10
        fixture.controller.mCurrentSecurityMode = KeyguardSecurityModel.SecurityMode.PIN
        assertEquals(KeyguardSecurityDisposition.StaleContext, bridge.complete(CredentialCheckResult.Rejected))
        assertTrue(fixture.callback.attempts.isEmpty())
        assertTrue(fixture.callback.dismissals.isEmpty())
    }

    /** 输入通知不得携带凭据，只复用原生防休眠语义。 */
    @Test
    fun userInputSignalsNativeCallback() {
        /** 当前 PIN 模式测试装配。 */
        val fixture = Fixture(KeyguardSecurityModel.SecurityMode.PIN)
        fixture.bind().signalUserInput()
        assertEquals(1, fixture.callback.userInputCount)
        assertEquals(1, fixture.callback.userActivityCount)
    }

    /** 为每个测试提供互不共享的 Titan 2 控制器装配。 */
    private class Fixture(mode: KeyguardSecurityModel.SecurityMode) {
        /** 测试 LockPatternUtils。 */
        val lockPatternUtils: LockPatternUtils = LockPatternUtils()

        /** 记录所有原生安全动作的回调。 */
        val callback: RecordingKeyguardSecurityCallback = RecordingKeyguardSecurityCallback()

        /** 可在异步结果到达前切换的当前用户。 */
        val user: SelectedUserInteractor = SelectedUserInteractor(10)

        /** 当前安全模式模型。 */
        private val model: KeyguardSecurityModel = KeyguardSecurityModel(mode)

        /** 待绑定的 Titan 2 控制器。 */
        val controller: KeyguardSecurityContainerController = KeyguardSecurityContainerController(
            mLockPatternUtils = lockPatternUtils,
            mKeyguardSecurityCallback = callback,
            mSecurityModel = model,
            mSelectedUserInteractor = user,
            mCurrentSecurityMode = mode,
        )

        /** 使用测试类加载器建立精确反射绑定。 */
        fun bind(): Titan2KeyguardSecurityBridge = Titan2KeyguardSecurityBridge.bind(
            controller = controller,
            classLoader = javaClass.classLoader!!,
        )
    }
}
