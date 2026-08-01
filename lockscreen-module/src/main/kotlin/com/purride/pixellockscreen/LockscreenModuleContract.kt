package com.purride.pixellockscreen

/**
 * 锁屏模块在完成目标 ROM 侦察前必须遵守的静态安全边界。
 *
 * 当前模块不会注册 LSPosed 入口，也不会向 SystemUI 注入任何代码；这些常量只让构建测试
 * 能明确锁定未来的唯一作用域和默认关闭策略。
 */
internal object LockscreenModuleContract {
    /** 未来 LSPosed 模块唯一允许声明的目标包。 */
    const val SYSTEM_UI_PACKAGE: String = "com.android.systemui"

    /** 未识别目标 ROM、SystemUI 架构或 LSPosed API 时必须保持关闭。 */
    const val HOOK_ENABLED_BY_DEFAULT: Boolean = false
}
