package com.purride.pixellockscreen.credential

import com.purride.pixellockscreen.ui.PinCredentialUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 特殊数字安全页协调器不缓存凭据且完整转发原生动作的测试。 */
class SpecialPinCredentialCoordinatorTest {
    /** 数字、删除、确认和紧急动作必须各自只转发一次。 */
    @Test
    fun actionsAreForwardedWithoutBuildingModuleCredential() {
        /** 记录原生动作和脱敏长度的测试边界。 */
        val actions = RecordingActions()
        /** 当前协调器提交的像素状态。 */
        val states = mutableListOf<PinCredentialUiState>()
        /** 原生紧急入口调用次数。 */
        var emergencyCount = 0
        /** 待测试的无凭据协调器。 */
        val coordinator = SpecialPinCredentialCoordinator(
            mode = Titan2SpecialPinMode.SIM_1,
            actions = actions,
            onEmergencyAction = { emergencyCount += 1 },
            onStateChanged = states::add,
            onInteractionFailed = { throwable -> throw throwable },
        )

        coordinator.refresh()
        coordinator.onDigitEntered('7')
        coordinator.onDeleteRequested()
        coordinator.onConfirmRequested()
        coordinator.onEmergencyRequested()

        assertEquals(listOf('7'), actions.digits)
        assertEquals(1, actions.deleteCount)
        assertEquals(1, actions.confirmCount)
        assertEquals(1, emergencyCount)
        assertEquals(listOf(0, 1, 0, 0), states.map { state -> state.inputLength })
    }

    /** 关闭后不得再调用任何原生安全动作。 */
    @Test
    fun closeRejectsFutureActions() {
        /** 不需要返回内容的测试边界。 */
        val actions = RecordingActions()
        /** 已关闭的协调器。 */
        val coordinator = SpecialPinCredentialCoordinator(
            mode = Titan2SpecialPinMode.ANTI_THEFT,
            actions = actions,
            onEmergencyAction = {},
            onStateChanged = {},
            onInteractionFailed = {},
        )
        coordinator.close()

        assertThrows(IllegalStateException::class.java) { coordinator.refresh() }
        assertThrows(IllegalStateException::class.java) { coordinator.onDigitEntered('1') }
        assertEquals(emptyList<Char>(), actions.digits)
    }

    /** 只记录动作与掩码长度、不保存完整输入的测试原生边界。 */
    private class RecordingActions : Titan2SpecialPinActions {
        /** 每次数字事件的独立记录。 */
        val digits: MutableList<Char> = mutableListOf()

        /** 删除动作次数。 */
        var deleteCount: Int = 0

        /** 确认动作次数。 */
        var confirmCount: Int = 0

        /** 当前模拟的原生掩码长度。 */
        private var inputLength: Int = 0

        /** 返回只包含长度的测试快照。 */
        override fun snapshot(): Titan2SpecialPinSnapshot = Titan2SpecialPinSnapshot(
            inputLength = inputLength,
            messageText = "",
            checking = false,
        )

        /** 记录单个数字并模拟原生掩码增长。 */
        override fun enterDigit(digit: Char) {
            digits += digit
            inputLength += 1
        }

        /** 记录删除并模拟原生掩码缩短。 */
        override fun delete() {
            deleteCount += 1
            inputLength = (inputLength - 1).coerceAtLeast(0)
        }

        /** 记录确认并模拟原生页面清空输入。 */
        override fun confirm() {
            confirmCount += 1
            inputLength = 0
        }
    }
}
