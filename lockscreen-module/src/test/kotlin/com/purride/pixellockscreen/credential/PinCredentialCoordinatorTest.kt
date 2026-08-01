package com.purride.pixellockscreen.credential

import com.purride.pixellockscreen.ui.PinCredentialFeedback
import com.purride.pixellockscreen.ui.PinCredentialUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 像素 PIN 协调器的可清零输入与状态测试。 */
class PinCredentialCoordinatorTest {
    /** UI 只能收到输入长度，删除后长度应同步减少。 */
    @Test
    fun digitsAndDeleteExposeOnlyLength() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()

        fixture.coordinator.onDigitEntered('1')
        fixture.coordinator.onDigitEntered('9')
        fixture.coordinator.onDeleteRequested()

        assertEquals(listOf(1, 2, 1), fixture.states.map(PinCredentialUiState::inputLength))
        assertEquals(3, fixture.userInputCount)
        assertEquals(0, fixture.submittedCredentials.size)
    }

    /** 少于四位的 PIN 只显示本地提示并立即清零。 */
    @Test
    fun shortPinIsRejectedBeforeSystemCheck() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()
        repeat(3) { digit -> fixture.coordinator.onDigitEntered(('1'.code + digit).toChar()) }

        fixture.coordinator.onConfirmRequested()

        assertEquals(0, fixture.submittedCredentials.size)
        assertEquals(0, fixture.states.last().inputLength)
        assertEquals(PinCredentialFeedback.ERROR, fixture.states.last().feedback)
        assertEquals("NEED 4+", fixture.states.last().feedbackText)
    }

    /** 合格 PIN 必须以可清零独占 lease 一次性移交。 */
    @Test
    fun validPinTransfersOneEphemeralLease() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()
        listOf('2', '4', '6', '8').forEach(fixture.coordinator::onDigitEntered)

        fixture.coordinator.onConfirmRequested()

        assertEquals(PinCredentialFeedback.CHECKING, fixture.states.last().feedback)
        assertEquals(1, fixture.submittedCredentials.size)
        /** 当前唯一提交的可清零 PIN。 */
        val lease = fixture.submittedCredentials.single()
        assertEquals(PixelCredentialMode.PIN, lease.mode)
        lease.withCharacters { characters ->
            assertEquals(listOf('2', '4', '6', '8'), List(characters.length) { index -> characters[index] })
        }
        lease.close()
    }

    /** Android 启用自动确认时应在精确长度到达后直接提交一次。 */
    @Test
    fun autoConfirmSubmitsAtConfiguredLength() {
        /** 使用六位自动确认设置的测试夹具。 */
        val fixture = Fixture(autoConfirmLength = 6)

        listOf('1', '3', '5', '7', '9', '0').forEach(fixture.coordinator::onDigitEntered)
        fixture.coordinator.onDigitEntered('8')
        fixture.coordinator.onConfirmRequested()

        assertEquals(1, fixture.submittedCredentials.size)
        assertEquals(PinCredentialFeedback.CHECKING, fixture.states.last().feedback)
        /** 自动确认提交的唯一 PIN lease。 */
        val lease = fixture.submittedCredentials.single()
        lease.withCharacters { characters ->
            assertEquals(
                listOf('1', '3', '5', '7', '9', '0'),
                List(characters.length) { index -> characters[index] },
            )
        }
        lease.close()
    }

    /** 系统校验或限流期间的迟到键盘事件必须被忽略。 */
    @Test
    fun disabledStateIgnoresLateKeyboardEvents() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()
        fixture.coordinator.showLockedOut(30)
        /** 限流前最后一次稳定状态数量。 */
        val stateCount = fixture.states.size

        fixture.coordinator.onDigitEntered('1')
        fixture.coordinator.onDeleteRequested()
        fixture.coordinator.onConfirmRequested()

        assertEquals(stateCount, fixture.states.size)
        assertEquals(0, fixture.userInputCount)
        assertEquals(0, fixture.submittedCredentials.size)
    }

    /** 自动确认长度必须位于系统和安全缓冲共同支持的范围。 */
    @Test
    fun invalidAutoConfirmLengthIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Fixture(autoConfirmLength = 3) }
        assertThrows(IllegalArgumentException::class.java) { Fixture(autoConfirmLength = 65) }
    }

    /** 非数字输入必须在进入安全缓冲前被拒绝。 */
    @Test
    fun nonDigitIsRejected() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()
        assertThrows(IllegalArgumentException::class.java) {
            fixture.coordinator.onDigitEntered('A')
        }
        assertEquals(0, fixture.states.size)
    }

    /** 释放后不得继续接收 PIN。 */
    @Test
    fun closedCoordinatorRejectsFutureInput() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()
        fixture.coordinator.onDigitEntered('1')
        fixture.coordinator.close()

        assertThrows(IllegalStateException::class.java) {
            fixture.coordinator.onDigitEntered('2')
        }
        assertEquals(0, fixture.submittedCredentials.size)
    }

    /** 为每个测试记录非敏感状态与临时 lease。 */
    private class Fixture(
        /** 当前测试模拟的 Android 自动确认长度。 */
        autoConfirmLength: Int? = null,
    ) {
        /** 原生用户活动通知次数。 */
        var userInputCount: Int = 0

        /** 提交给模拟系统桥的独占 PIN。 */
        val submittedCredentials: MutableList<EphemeralCredentialLease.Characters> = mutableListOf()

        /** 协调器提交过的非敏感 UI 状态。 */
        val states: MutableList<PinCredentialUiState> = mutableListOf()

        /** 当前测试使用的 PIN 协调器。 */
        val coordinator: PinCredentialCoordinator = PinCredentialCoordinator(
            autoConfirmLength = autoConfirmLength,
            onUserInput = { userInputCount += 1 },
            onCredentialReady = { credential -> submittedCredentials += credential },
            onEmergencyAction = {},
            onStateChanged = states::add,
            onInteractionFailed = { throwable -> throw throwable },
        )

        /** 模拟运行时会话完成绑定后的首次可输入状态。 */
        init {
            coordinator.showReady()
            states.clear()
        }
    }
}
