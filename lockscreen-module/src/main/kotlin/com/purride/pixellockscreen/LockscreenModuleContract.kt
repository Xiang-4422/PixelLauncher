package com.purride.pixellockscreen

/**
 * 锁定像素锁屏模块的作用域和当前里程碑能力边界。
 *
 * M4 允许在已识别 Titan 2 SystemUI 中安装只读探测 Hook，但严禁隐藏或覆盖
 * 原生 Keyguard；完整视觉接管必须在 M5 完成首帧回退机制后单独启用。
 */
internal object LockscreenModuleContract {
    /** Modern Xposed 模块唯一允许声明的目标包。 */
    const val SYSTEM_UI_PACKAGE: String = "com.android.systemui"

    /** SystemUI 的主进程名，禁止向次要进程安装锁屏 Hook。 */
    const val SYSTEM_UI_PROCESS: String = "com.android.systemui"

    /** M4 允许在精确命中目标合同后安装只读签名探测。 */
    const val READ_ONLY_HOOK_ENABLED: Boolean = true

    /** M4 明确保持关闭的视觉接管开关。 */
    const val VISUAL_TAKEOVER_ENABLED: Boolean = false
}
