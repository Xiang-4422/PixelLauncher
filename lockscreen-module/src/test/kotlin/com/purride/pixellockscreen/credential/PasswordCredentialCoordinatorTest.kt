package com.purride.pixellockscreen.credential

import com.purride.pixellockscreen.ui.PasswordCredentialFeedback
import com.purride.pixellockscreen.ui.PasswordCredentialUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 原生密码状态到像素界面状态的无凭据协调测试。 */
class PasswordCredentialCoordinatorTest {
    /** 初始化和输入变化只能输出长度、焦点及 IME 入口状态。 */
    @Test
    fun mirrorsLengthFocusAndImeVisibility() {
        /** 当前测试夹具。 */
        val fixture = Fixture()
        fixture.coordinator.showInitial(length = 7, focused = true, imeVisible = true)
        assertEquals(7, fixture.states.last().inputLength)
        assertTrue(fixture.states.last().hasInputFocus)
        assertTrue(fixture.states.last().isImeSwitcherVisible)

        fixture.coordinator.onNativeInputLengthChanged(8)
        fixture.coordinator.onNativeFocusChanged(false)
        fixture.coordinator.onImeSwitcherVisibilityChanged(false)
        assertEquals(8, fixture.states.last().inputLength)
        assertFalse(fixture.states.last().hasInputFocus)
        assertFalse(fixture.states.last().isImeSwitcherVisible)
    }

    /** 校验、拒绝、限流和恢复必须严格反映原生认证阶段。 */
    @Test
    fun reflectsNativeVerificationLifecycle() {
        /** 当前测试夹具。 */
        val fixture = Fixture()
        fixture.coordinator.showInitial(length = 4, focused = true, imeVisible = false)
        fixture.coordinator.showChecking()
        assertEquals(PasswordCredentialFeedback.CHECKING, fixture.states.last().feedback)
        fixture.coordinator.showRejected()
        assertEquals(PasswordCredentialFeedback.ERROR, fixture.states.last().feedback)
        fixture.coordinator.onNativeInputLengthChanged(1)
        assertEquals(PasswordCredentialFeedback.READY, fixture.states.last().feedback)
        fixture.coordinator.showLockedOut(30)
        assertEquals(PasswordCredentialFeedback.LOCKED_OUT, fixture.states.last().feedback)
        assertEquals("WAIT 30S", fixture.states.last().feedbackText)
        fixture.coordinator.showReady()
        assertEquals(PasswordCredentialFeedback.READY, fixture.states.last().feedback)
    }

    /** 三个像素动作必须只转发到对应原生入口。 */
    @Test
    fun forwardsOnlyPublicNativeActions() {
        /** 当前测试夹具。 */
        val fixture = Fixture()
        fixture.coordinator.onInputRequested()
        fixture.coordinator.onImeSwitcherRequested()
        fixture.coordinator.onEmergencyRequested()
        assertEquals(1, fixture.inputRequests)
        assertEquals(1, fixture.imeRequests)
        assertEquals(1, fixture.emergencyRequests)
    }

    /** 动作异常必须上报一次，关闭后不得继续调用外部对象。 */
    @Test
    fun reportsActionFailureAndStopsAfterClose() {
        /** 当前测试收到的异常。 */
        val failures = mutableListOf<Throwable>()
        /** 会抛出异常的协调器。 */
        val coordinator = PasswordCredentialCoordinator(
            onInputRequestedAction = { error("input_failure") },
            onImeSwitcherRequestedAction = {},
            onEmergencyAction = {},
            onStateChanged = {},
            onInteractionFailed = failures::add,
        )
        coordinator.onInputRequested()
        coordinator.close()
        coordinator.onInputRequested()
        assertEquals(1, failures.size)
    }

    /** 记录全部非敏感输出和公开动作的测试夹具。 */
    private class Fixture {
        /** 收到的像素状态。 */
        val states: MutableList<PasswordCredentialUiState> = mutableListOf()

        /** 输入请求次数。 */
        var inputRequests: Int = 0

        /** 输入法选择请求次数。 */
        var imeRequests: Int = 0

        /** 紧急入口请求次数。 */
        var emergencyRequests: Int = 0

        /** 被测协调器。 */
        val coordinator: PasswordCredentialCoordinator = PasswordCredentialCoordinator(
            onInputRequestedAction = { inputRequests += 1 },
            onImeSwitcherRequestedAction = { imeRequests += 1 },
            onEmergencyAction = { emergencyRequests += 1 },
            onStateChanged = states::add,
            onInteractionFailed = { throw AssertionError(it) },
        )
    }
}
