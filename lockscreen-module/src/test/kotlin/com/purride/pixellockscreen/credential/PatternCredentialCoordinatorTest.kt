package com.purride.pixellockscreen.credential

import com.purride.pixellockscreen.ui.PatternCredentialFeedback
import com.purride.pixellockscreen.ui.PatternCredentialUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 像素图案运行时协调器的安全输入与状态测试。 */
class PatternCredentialCoordinatorTest {
    /** 少于四格的图案只显示本地提示，不得创建系统校验任务。 */
    @Test
    fun shortPatternIsRejectedBeforeSystemCheck() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()

        fixture.coordinator.onPatternStarted()
        repeat(3) { cellId -> fixture.coordinator.onPatternCellAdded(cellId) }
        fixture.coordinator.onPatternCompleted(3)

        assertEquals(1, fixture.userInputCount)
        assertEquals(0, fixture.submittedPatterns.size)
        assertEquals(PatternCredentialFeedback.ERROR, fixture.states.last().feedback)
        assertEquals("USE 4+ DOTS", fixture.states.last().feedbackText)
    }

    /** 合格路径只移交一次独占 lease，并立即进入不可输入的校验状态。 */
    @Test
    fun validPatternTransfersOneEphemeralLease() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()

        fixture.coordinator.onPatternStarted()
        listOf(0, 1, 4, 8).forEach(fixture.coordinator::onPatternCellAdded)
        fixture.coordinator.onPatternCompleted(4)

        assertEquals(PatternCredentialFeedback.CHECKING, fixture.states.last().feedback)
        assertEquals(1, fixture.submittedPatterns.size)
        /** 唯一提交给系统桥的临时图案。 */
        val lease = fixture.submittedPatterns.single()
        assertEquals(4, lease.size)
        assertEquals(listOf(0, 1, 4, 8), List(lease.size, lease::cellAt))
        lease.close()
    }

    /** 手势报告长度与安全缓冲不一致时必须失效关闭而不是猜测路径。 */
    @Test
    fun completedCountMustMatchSecureBuffer() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()
        fixture.coordinator.onPatternStarted()
        fixture.coordinator.onPatternCellAdded(0)

        assertThrows(IllegalStateException::class.java) {
            fixture.coordinator.onPatternCompleted(2)
        }
        assertEquals(0, fixture.submittedPatterns.size)
    }

    /** 紧急入口应独立于凭据提交直接转交外部原生桥。 */
    @Test
    fun emergencyRequestDoesNotSubmitCredential() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()

        fixture.coordinator.onEmergencyRequested()

        assertEquals(1, fixture.emergencyCount)
        assertEquals(0, fixture.submittedPatterns.size)
    }

    /** 释放会清零会话并拒绝所有后续输入。 */
    @Test
    fun closedCoordinatorRejectsFutureInput() {
        /** 记录协调器输出的测试夹具。 */
        val fixture = Fixture()
        fixture.coordinator.onPatternStarted()
        fixture.coordinator.onPatternCellAdded(0)
        fixture.coordinator.close()

        assertThrows(IllegalStateException::class.java) {
            fixture.coordinator.onPatternCellAdded(1)
        }
        assertEquals(0, fixture.submittedPatterns.size)
    }

    /** 为每个测试记录非敏感输出和临时 lease。 */
    private class Fixture {
        /** 原生用户输入通知次数。 */
        var userInputCount: Int = 0

        /** 原生紧急操作请求次数。 */
        var emergencyCount: Int = 0

        /** 提交给模拟系统桥的独占图案。 */
        val submittedPatterns: MutableList<EphemeralCredentialLease.Pattern> = mutableListOf()

        /** 协调器提交过的非敏感 UI 状态。 */
        val states: MutableList<PatternCredentialUiState> = mutableListOf()

        /** 当前测试使用的协调器。 */
        val coordinator: PatternCredentialCoordinator = PatternCredentialCoordinator(
            onUserInput = { userInputCount += 1 },
            onCredentialReady = { lease -> submittedPatterns += lease },
            onEmergencyAction = { emergencyCount += 1 },
            onStateChanged = states::add,
            onInteractionFailed = { throwable -> throw throwable },
        )
    }
}
