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

    /** 当前 SystemUI 进程中已挂载的主安全容器控制器。 */
    private var activeSecurityController: Any? = null

    /** 当前 SystemUI 进程中唯一活跃的像素图案认证会话。 */
    private var activePatternSession: PixelPatternSecuritySession? = null

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
            if (LockscreenModuleContract.PATTERN_TAKEOVER_ENABLED) {
                installPatternSecurityHooks(classLoader)
            } else {
                log(Log.INFO, LOG_TAG, "pattern_hooks_disabled")
            }
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
            newSession.setCredentialTakeoverActive(
                activePatternSession?.isTakeoverActive() == true,
            )
            log(Log.INFO, LOG_TAG, "visual_session_started")
        } catch (throwable: Throwable) {
            if (activeSession === newSession) {
                activeSession = null
            }
            log(Log.ERROR, LOG_TAG, "visual_session_failed", throwable)
        }
    }

    /** 安装主安全容器和图案控制器的精确生命周期 Hook。 */
    @SuppressLint("PrivateApi")
    private fun installPatternSecurityHooks(classLoader: ClassLoader) {
        /** Titan 2 主安全容器控制器类。 */
        val securityControllerClass = Class.forName(
            SECURITY_CONTAINER_CONTROLLER_CLASS,
            false,
            classLoader,
        )
        /** Titan 2 图案认证控制器类。 */
        val patternControllerClass = Class.forName(
            PATTERN_CONTROLLER_CLASS,
            false,
            classLoader,
        )
        /** 主安全容器完成挂载的方法。 */
        val securityAttachedMethod = exactVoidMethod(
            securityControllerClass,
            CONTROLLER_VIEW_ATTACHED_METHOD,
        )
        /** 主安全容器即将脱离的方法。 */
        val securityDetachedMethod = exactVoidMethod(
            securityControllerClass,
            CONTROLLER_VIEW_DETACHED_METHOD,
        )
        /** 图案认证页面恢复的方法。 */
        val patternResumeMethod = patternControllerClass.getDeclaredMethod(
            PATTERN_RESUME_METHOD,
            Int::class.javaPrimitiveType,
        )
        check(patternResumeMethod.returnType == Void.TYPE) { "pattern_resume_signature" }
        /** 图案认证页面暂停的方法。 */
        val patternPauseMethod = exactVoidMethod(patternControllerClass, PATTERN_PAUSE_METHOD)
        /** 图案认证页面即将脱离的方法。 */
        val patternDetachedMethod = exactVoidMethod(
            patternControllerClass,
            CONTROLLER_VIEW_DETACHED_METHOD,
        )

        hook(securityAttachedMethod)
            .setId(SECURITY_ATTACHED_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 原生挂载完成后才允许保存可用控制器。 */
                val result = chain.proceed()
                attachSecurityController(chain.thisObject)
                result
            })
        hook(securityDetachedMethod)
            .setId(SECURITY_DETACHED_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 原生对象脱离前先恢复像素会话持有的全部视图状态。 */
                detachSecurityController(chain.thisObject)
                chain.proceed()
            })
        hook(patternResumeMethod)
            .setId(PATTERN_RESUME_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 原生页面完成恢复和紧急按钮准备后再尝试像素接管。 */
                val result = chain.proceed()
                startPatternSession(chain.thisObject, classLoader)
                result
            })
        hook(patternPauseMethod)
            .setId(PATTERN_PAUSE_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 页面暂停前立即清零输入并恢复原生内容。 */
                stopPatternSession(chain.thisObject)
                chain.proceed()
            })
        hook(patternDetachedMethod)
            .setId(PATTERN_DETACHED_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 页面重建前再次执行幂等回退。 */
                stopPatternSession(chain.thisObject)
                chain.proceed()
            })
        log(Log.INFO, LOG_TAG, "pattern_hooks_installed")
    }

    /** 保存当前主安全容器；对象替换时先结束旧图案会话。 */
    private fun attachSecurityController(controller: Any?) {
        if (controller == null) {
            log(Log.ERROR, LOG_TAG, "security_controller_missing")
            return
        }
        if (activeSecurityController === controller) {
            return
        }
        activePatternSession?.dispose()
        activeSecurityController = controller
        log(Log.INFO, LOG_TAG, "security_controller_attached")
    }

    /** 主安全容器脱离前结束其图案会话并清除对象引用。 */
    private fun detachSecurityController(controller: Any?) {
        if (controller == null || activeSecurityController !== controller) {
            return
        }
        activePatternSession?.dispose()
        activeSecurityController = null
        log(Log.INFO, LOG_TAG, "security_controller_detached")
    }

    /** 在原生图案页完成恢复后幂等创建像素认证会话。 */
    private fun startPatternSession(patternController: Any?, classLoader: ClassLoader) {
        if (patternController == null) {
            log(Log.ERROR, LOG_TAG, "pattern_controller_missing")
            return
        }
        /** 当前已挂载的主安全容器。 */
        val securityController = activeSecurityController ?: run {
            log(Log.WARN, LOG_TAG, "pattern_security_controller_unavailable")
            return
        }
        /** 同一原生图案控制器的重复恢复沿用现有会话。 */
        val previousSession = activePatternSession
        if (previousSession?.isBoundTo(patternController) == true) {
            log(Log.INFO, LOG_TAG, "pattern_session_reused")
            return
        }
        previousSession?.dispose()
        /** 只有通过全部运行时合同才会进入首帧等待的新会话。 */
        val newSession = PixelPatternSecuritySession(
            securityController = securityController,
            patternController = patternController,
            classLoader = classLoader,
            onTakeoverChanged = { active ->
                activeSession?.setCredentialTakeoverActive(active)
            },
            onFailure = { failedSession, throwable ->
                if (activePatternSession === failedSession) {
                    log(Log.ERROR, LOG_TAG, "pattern_session_runtime_failed", throwable)
                }
            },
            onDisposed = { disposedSession ->
                if (activePatternSession === disposedSession) {
                    activePatternSession = null
                }
            },
        )
        activePatternSession = newSession
        try {
            newSession.start()
            log(Log.INFO, LOG_TAG, "pattern_session_started")
        } catch (throwable: Throwable) {
            if (activePatternSession === newSession) {
                activePatternSession = null
            }
            log(Log.ERROR, LOG_TAG, "pattern_session_start_failed", throwable)
        }
    }

    /** 只结束绑定指定原生控制器的图案会话。 */
    private fun stopPatternSession(patternController: Any?) {
        if (patternController == null) {
            return
        }
        /** 当前可能属于其他新控制器的图案会话。 */
        val session = activePatternSession ?: return
        if (session.isBoundTo(patternController)) {
            session.dispose()
            log(Log.INFO, LOG_TAG, "pattern_session_stopped")
        }
    }

    /** 解析并验证一个控制器声明的无参 void 方法。 */
    private fun exactVoidMethod(owner: Class<*>, name: String): java.lang.reflect.Method {
        /** 当前目标控制器方法。 */
        val method = owner.getDeclaredMethod(name)
        check(method.parameterCount == 0 && method.returnType == Void.TYPE) {
            "controller_method_signature:$name"
        }
        return method
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

        /** Titan 2 主安全容器控制器类名。 */
        const val SECURITY_CONTAINER_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardSecurityContainerController"

        /** Titan 2 图案认证控制器类名。 */
        const val PATTERN_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPatternViewController"

        /** 控制器视图挂载方法名。 */
        const val CONTROLLER_VIEW_ATTACHED_METHOD: String = "onViewAttached"

        /** 控制器视图脱离方法名。 */
        const val CONTROLLER_VIEW_DETACHED_METHOD: String = "onViewDetached"

        /** 图案页面恢复方法名。 */
        const val PATTERN_RESUME_METHOD: String = "onResume"

        /** 图案页面暂停方法名。 */
        const val PATTERN_PAUSE_METHOD: String = "onPause"

        /** 主安全容器挂载 Hook ID。 */
        const val SECURITY_ATTACHED_HOOK_ID: String =
            "pixel_lockscreen:security_container_attached"

        /** 主安全容器脱离 Hook ID。 */
        const val SECURITY_DETACHED_HOOK_ID: String =
            "pixel_lockscreen:security_container_detached"

        /** 图案页面恢复 Hook ID。 */
        const val PATTERN_RESUME_HOOK_ID: String = "pixel_lockscreen:pattern_resume"

        /** 图案页面暂停 Hook ID。 */
        const val PATTERN_PAUSE_HOOK_ID: String = "pixel_lockscreen:pattern_pause"

        /** 图案页面脱离 Hook ID。 */
        const val PATTERN_DETACHED_HOOK_ID: String = "pixel_lockscreen:pattern_detached"
    }
}
