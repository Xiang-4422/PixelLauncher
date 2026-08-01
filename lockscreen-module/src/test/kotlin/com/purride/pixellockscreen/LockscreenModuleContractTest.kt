package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 锁定模块在设备兼容性确认前的最小作用域与默认关闭策略。 */
class LockscreenModuleContractTest {
    /** 模块只能注入 SystemUI，禁止把系统框架或普通应用扩大进作用域。 */
    @Test
    fun targetPackageRemainsSystemUiOnly() {
        assertEquals("com.android.systemui", LockscreenModuleContract.SYSTEM_UI_PACKAGE)
    }

    /** 尚未建立 ROM 适配器时，任何构建都不能默认启用 Hook。 */
    @Test
    fun hookRemainsDisabledBeforeReconnaissance() {
        assertFalse(LockscreenModuleContract.HOOK_ENABLED_BY_DEFAULT)
    }
}
