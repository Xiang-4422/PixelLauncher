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
    private class Fixture {
        /** 原生用户活动通知次数。 */
        var userInputCount: Int = 0

        /** 提交给模拟系统桥的独占 PIN。 */
        val submittedCredentials: MutableList<EphemeralCredentialLease.Characters> = mutableListOf()

        /** 协调器提交过的非敏感 UI 状态。 */
        val states: MutableList<PinCredentialUiState> = mutableListOf()

        /** 当前测试使用的 PIN 协调器。 */
        val coordinator: PinCredentialCoordinator = PinCredentialCoordinator(
            onUserInput = { userInputCount += 1 },
            onCredentialReady = { credential -> submittedCredentials += credential },
            onEmergencyAction = {},
            onStateChanged = states::add,
            onInteractionFailed = { throwable -> throw throwable },
        )
    }
}
