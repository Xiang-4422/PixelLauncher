package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** 锁定 M5 的唯一作用域、普通锁屏接管与凭据边界。 */
class LockscreenModuleContractTest {
    /** 模块只能注入 SystemUI 主进程，禁止扩大到系统框架或普通应用。 */
    @Test
    fun targetPackageAndProcessRemainSystemUiOnly() {
        assertEquals("com.android.systemui", LockscreenModuleContract.SYSTEM_UI_PACKAGE)
        assertEquals("com.android.systemui", LockscreenModuleContract.SYSTEM_UI_PROCESS)
    }

    /** M5 启用普通锁屏接管，但 M6 之前必须保留原生凭据 Bouncer。 */
    @Test
    fun milestoneFiveKeepsCredentialTakeoverDisabled() {
        assertTrue(LockscreenModuleContract.READ_ONLY_HOOK_ENABLED)
        assertTrue(LockscreenModuleContract.VISUAL_TAKEOVER_ENABLED)
        assertFalse(LockscreenModuleContract.PATTERN_TAKEOVER_ENABLED)
    }
}
