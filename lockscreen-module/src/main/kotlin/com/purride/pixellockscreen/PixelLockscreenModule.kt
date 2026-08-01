package com.purride.pixellockscreen

import android.annotation.SuppressLint
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 像素锁屏的 Modern Xposed API 102 入口。
 *
 * 入口只在 Titan 2 精确设备合同命中后 Hook Keyguard 启动点。M4 拦截器先执行
 * 原生 `start()`，之后只读探测必需视图。M5 只有在全部签名和恢复事务就绪后
 * 挂载普通像素 Keyguard，原生凭据 Bouncer 始终保留。
 */
public class PixelLockscreenModule : XposedModule() {
    /** 模块加载阶段记录的进程名，只用于后续兼容性判定。 */
    private var loadedProcessName: String? = null

    /** 当前 SystemUI 进程中唯一活跃的像素 Keyguard 会话。 */
    private var activeSession: PixelKeyguardSession? = null

    /** 记录当前进程并立即脱离非 SystemUI 主进程。 */
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        /** 框架报告的当前进程名。 */
        val processName = param.processName
        if (param.isSystemServer || processName != LockscreenModuleContract.SYSTEM_UI_PROCESS) {
            log(Log.WARN, LOG_TAG, "skip_process")
            detach()
            return
        }
        loadedProcessName = processName
    }

    /** 在 SystemUI 最终类加载器就绪后执行精确合同检查并安装单一 Hook。 */
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        /** 必须来自已通过进程门禁的加载回调。 */
        val processName = loadedProcessName ?: run {
            log(Log.ERROR, LOG_TAG, "missing_process_state")
            detach()
            return
        }
        /** 不持有 Android 对象的兼容性快照。 */
        val environment = Titan2SystemUiTarget.environmentOf(processName, param)
        /** 对当前包、进程、SDK、指纹和 APK 路径的门禁结果。 */
        when (val decision = Titan2SystemUiTarget.evaluate(environment)) {
            SystemUiCompatibilityDecision.Supported -> installReadOnlyProbe(param.classLoader)
            is SystemUiCompatibilityDecision.Rejected -> {
                log(Log.WARN, LOG_TAG, "target_rejected:${decision.reasonCode}")
                detach()
            }
        }
    }

    /**
     * Hook 已验证的 Keyguard 启动方法，并在原方法完成后执行只读视图探测。
     *
     * @param classLoader SystemUI 最终应用类加载器。
     */
    @SuppressLint("PrivateApi")
    private fun installReadOnlyProbe(classLoader: ClassLoader) {
        if (!LockscreenModuleContract.READ_ONLY_HOOK_ENABLED) {
            log(Log.WARN, LOG_TAG, "read_only_hook_disabled")
            detach()
            return
        }
        try {
            /** Titan 2 传统 View Keyguard 的启动配置类。 */
            val configuratorClass = Class.forName(KEYGUARD_CONFIGURATOR_CLASS, false, classLoader)
            /** 无参数的 CoreStartable 启动方法。 */
            val startMethod = configuratorClass.getDeclaredMethod(KEYGUARD_START_METHOD)
            check(startMethod.parameterCount == 0 && startMethod.returnType == Void.TYPE) {
                "start_signature"
            }
            hook(startMethod)
                .setId(KEYGUARD_START_HOOK_ID)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(XposedInterface.Hooker { chain ->
                    /** 始终先执行原生启动链，原生异常保持原有语义。 */
                    val result = chain.proceed()
                    inspectStartedConfigurator(chain.thisObject)
                    result
                })
            log(Log.INFO, LOG_TAG, "probe_hook_installed")
        } catch (throwable: Throwable) {
            log(Log.ERROR, LOG_TAG, "probe_hook_install_failed", throwable)
            detach()
        }
    }

    /** 探测完成启动的 configurator，并在 M5 合同就绪后启动可回退锁屏会话。 */
    private fun inspectStartedConfigurator(configurator: Any?) {
        if (configurator == null) {
            log(Log.ERROR, LOG_TAG, "probe_missing_instance")
            return
        }
        runCatching { Titan2SystemUiProbe.bind(configurator) }
            .onSuccess { binding ->
                /** 不持有 View 的日志摘要。 */
                val result = binding.toProbeResult()
                /** 仅包含类名的探测摘要，不记录视图内容或用户数据。 */
                val classSummary = listOf(
                    result.keyguardRootClassName,
                    result.shadeWindowClassName,
                    result.bouncerContainerClassName,
                ).joinToString(separator = ",")
                log(Log.INFO, LOG_TAG, "probe_ready:$classSummary")
                if (LockscreenModuleContract.VISUAL_TAKEOVER_ENABLED) {
                    startVisualSession(binding)
                }
            }
            .onFailure { throwable ->
                log(Log.ERROR, LOG_TAG, "probe_failed", throwable)
            }
    }

    /** 幂等替换旧根视图会话，同一根视图的重复 `start()` 不会创建二次宿主。 */
    private fun startVisualSession(binding: Titan2SystemUiBinding) {
        /** 可能由同一 configurator 重复启动的现有会话。 */
        val previousSession = activeSession
        if (previousSession?.isBoundTo(binding.keyguardRoot) == true) {
            log(Log.INFO, LOG_TAG, "visual_session_reused")
            return
        }
        previousSession?.dispose()
        /** 已通过完整视图合同的新像素 Keyguard 会话。 */
        val newSession = PixelKeyguardSession(binding) { disposedSession ->
            if (activeSession === disposedSession) {
                activeSession = null
            }
        }
        activeSession = newSession
        try {
            newSession.start()
            log(Log.INFO, LOG_TAG, "visual_session_started")
        } catch (throwable: Throwable) {
            if (activeSession === newSession) {
                activeSession = null
            }
            log(Log.ERROR, LOG_TAG, "visual_session_failed", throwable)
        }
    }

    private companion object {
        /** logcat 中唯一且稳定的模块标签。 */
        const val LOG_TAG: String = "PixelLockscreen"

        /** Titan 2 已证实实例化并执行的 Keyguard 配置类。 */
        const val KEYGUARD_CONFIGURATOR_CLASS: String =
            "com.android.systemui.keyguard.KeyguardViewConfigurator"

        /** Keyguard 配置器的无参数启动方法。 */
        const val KEYGUARD_START_METHOD: String = "start"

        /** 支持 Modern Xposed 热更新原子替换的稳定 Hook ID。 */
        const val KEYGUARD_START_HOOK_ID: String = "pixel_lockscreen:keyguard_start_probe"
    }
}
