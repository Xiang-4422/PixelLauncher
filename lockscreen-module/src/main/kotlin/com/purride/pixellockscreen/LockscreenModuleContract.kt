package com.purride.pixellockscreen

/**
 * 锁定像素锁屏模块的作用域和当前里程碑能力边界。
 *
 * M5 允许在已识别 Titan 2 SystemUI 中挂载普通像素 Keyguard。M6 已建立可清零凭据
 * 与系统校验桥，但图案、PIN、密码、SIM 和其他安全模式仍继续使用原生 Bouncer，
 * 直到像素输入宿主、系统解锁回调和完整回退事务一并适配完成。
 */
internal object LockscreenModuleContract {
    /** Modern Xposed 模块唯一允许声明的目标包。 */
    const val SYSTEM_UI_PACKAGE: String = "com.android.systemui"

    /** SystemUI 的主进程名，禁止向次要进程安装锁屏 Hook。 */
    const val SYSTEM_UI_PROCESS: String = "com.android.systemui"

    /** M4 允许在精确命中目标合同后安装只读签名探测。 */
    const val READ_ONLY_HOOK_ENABLED: Boolean = true

    /** M5 在像素首帧和恢复事务就绪后启用普通锁屏接管。 */
    const val VISUAL_TAKEOVER_ENABLED: Boolean = true

    /** M6 图案运行时已具备完整回退链，但在单独启用提交前不安装安全页面 Hook。 */
    const val PATTERN_TAKEOVER_ENABLED: Boolean = false
}
