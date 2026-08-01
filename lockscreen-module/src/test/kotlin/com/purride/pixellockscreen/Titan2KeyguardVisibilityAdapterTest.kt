package com.purride.pixellockscreen

import com.android.systemui.keyguard.KeyguardViewMediator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Titan 2 普通锁屏真实显示状态适配测试。 */
class Titan2KeyguardVisibilityAdapterTest {
    /** 显示且未遮挡的 Keyguard 必须保持像素普通锁屏接管。 */
    @Test
    fun showingKeyguardRemainsVisibleAcrossViewTransitions() {
        /** 模拟 View alpha 短暂变化期间仍为显示状态的原生协调器。 */
        val mediator = KeyguardViewMediator(mShowing = true, mOccluded = false)
        /** 当前精确字段合同绑定出的只读适配器。 */
        val adapter = Titan2KeyguardVisibilityAdapter.bind(mediator)
        assertTrue(adapter.isOrdinaryKeyguardVisible())
    }

    /** 真正解锁或被外部系统界面遮挡时必须恢复共享原生内容。 */
    @Test
    fun hiddenOrOccludedKeyguardIsNotOrdinaryLockscreen() {
        /** 当前可切换显示与遮挡组合的原生协调器。 */
        val mediator = KeyguardViewMediator(mShowing = false, mOccluded = false)
        /** 复用同一适配器证明字段按帧读取而不是缓存初始值。 */
        val adapter = Titan2KeyguardVisibilityAdapter.bind(mediator)
        assertFalse(adapter.isOrdinaryKeyguardVisible())

        mediator.mShowing = true
        mediator.mOccluded = true
        assertFalse(adapter.isOrdinaryKeyguardVisible())
    }

    /** 非目标类不得借助同名字段绕过 Titan 2 精确合同。 */
    @Test
    fun unrelatedMediatorClassIsRejected() {
        /** 非目标类的绑定结果。 */
        val result = runCatching { Titan2KeyguardVisibilityAdapter.bind(Any()) }
        assertTrue(result.isFailure)
    }
}
