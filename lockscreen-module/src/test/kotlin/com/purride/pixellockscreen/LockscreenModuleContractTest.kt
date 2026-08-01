package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定当前唯一作用域、普通锁屏接管与图案认证边界。 */
class LockscreenModuleContractTest {
    /** 模块只能注入 SystemUI 主进程，禁止扩大到系统框架或普通应用。 */
    @Test
    fun targetPackageAndProcessRemainSystemUiOnly() {
        assertEquals("com.android.systemui", LockscreenModuleContract.SYSTEM_UI_PACKAGE)
        assertEquals("com.android.systemui", LockscreenModuleContract.SYSTEM_UI_PROCESS)
    }

    /** 当前只启用已完成全链路回退的图案认证接管。 */
    @Test
    fun verifiedPatternTakeoverIsEnabled() {
        assertTrue(LockscreenModuleContract.READ_ONLY_HOOK_ENABLED)
        assertTrue(LockscreenModuleContract.VISUAL_TAKEOVER_ENABLED)
        assertTrue(LockscreenModuleContract.PATTERN_TAKEOVER_ENABLED)
    }
}
