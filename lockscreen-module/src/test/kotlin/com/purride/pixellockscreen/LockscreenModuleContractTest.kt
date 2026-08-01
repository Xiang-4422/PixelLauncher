package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定 M4 的唯一作用域、只读 Hook 与禁止视觉接管边界。 */
class LockscreenModuleContractTest {
    /** 模块只能注入 SystemUI 主进程，禁止扩大到系统框架或普通应用。 */
    @Test
    fun targetPackageAndProcessRemainSystemUiOnly() {
        assertEquals("com.android.systemui", LockscreenModuleContract.SYSTEM_UI_PACKAGE)
        assertEquals("com.android.systemui", LockscreenModuleContract.SYSTEM_UI_PROCESS)
    }

    /** M4 可以安装只读探测 Hook，但不能隐藏或覆盖原生 Keyguard。 */
    @Test
    fun milestoneFourOnlyEnablesReadOnlyProbe() {
        assertTrue(LockscreenModuleContract.READ_ONLY_HOOK_ENABLED)
        assertFalse(LockscreenModuleContract.VISUAL_TAKEOVER_ENABLED)
    }
}
