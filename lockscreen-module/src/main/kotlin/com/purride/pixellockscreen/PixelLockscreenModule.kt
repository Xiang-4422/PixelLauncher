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
 * 挂载普通像素 Keyguard；认证接管只隐藏原生凭据页面，控制器与系统安全链始终保留。
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

    /** 当前 SystemUI 进程中唯一活跃的像素 PIN 认证会话。 */
    private var activePinSession: PixelPinSecuritySession? = null

    /** 当前 SystemUI 进程中唯一活跃的像素密码认证会话。 */
    private var activePasswordSession: PixelPasswordSecuritySession? = null

    /** 当前 SystemUI 进程中唯一活跃的 SIM 或 AntiTheft 像素会话。 */
    private var activeSpecialPinSession: PixelSpecialPinSecuritySession? = null

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
            if (
                LockscreenModuleContract.PATTERN_TAKEOVER_ENABLED ||
                LockscreenModuleContract.PIN_TAKEOVER_ENABLED ||
                LockscreenModuleContract.PASSWORD_TAKEOVER_ENABLED ||
                LockscreenModuleContract.SPECIAL_PIN_TAKEOVER_ENABLED
            ) {
                installCredentialSecurityHooks(classLoader)
            } else {
                log(Log.INFO, LOG_TAG, "credential_hooks_disabled")
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
                    result.indicationControllerClassName,
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
            refreshCredentialTakeoverState()
            log(Log.INFO, LOG_TAG, "visual_session_started")
        } catch (throwable: Throwable) {
            if (activeSession === newSession) {
                activeSession = null
            }
            log(Log.ERROR, LOG_TAG, "visual_session_failed", throwable)
        }
    }

    /** 安装主安全容器以及当前已启用凭据控制器的精确生命周期 Hook。 */
    @SuppressLint("PrivateApi")
    private fun installCredentialSecurityHooks(classLoader: ClassLoader) {
        /** Titan 2 主安全容器控制器类。 */
        val securityControllerClass = Class.forName(
            SECURITY_CONTAINER_CONTROLLER_CLASS,
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
        if (LockscreenModuleContract.PATTERN_TAKEOVER_ENABLED) {
            installPatternSecurityHooks(classLoader)
        }
        if (LockscreenModuleContract.PIN_TAKEOVER_ENABLED) {
            installPinSecurityHooks(classLoader)
        } else {
            log(Log.INFO, LOG_TAG, "pin_hooks_disabled")
        }
        if (LockscreenModuleContract.PASSWORD_TAKEOVER_ENABLED) {
            installPasswordSecurityHooks(classLoader)
        } else {
            log(Log.INFO, LOG_TAG, "password_hooks_disabled")
        }
        if (LockscreenModuleContract.SPECIAL_PIN_TAKEOVER_ENABLED) {
            installSpecialPinSecurityHooks(classLoader)
        } else {
            log(Log.INFO, LOG_TAG, "special_pin_hooks_disabled")
        }
        log(Log.INFO, LOG_TAG, "credential_container_hooks_installed")
    }

    /** 安装 Titan 2 MediaTek SIM 与 AntiTheft 最终控制器的精确生命周期 Hook。 */
    @SuppressLint("PrivateApi")
    private fun installSpecialPinSecurityHooks(classLoader: ClassLoader) {
        /** Titan 2 SIM PIN/PUK/ME 最终控制器类。 */
        val simControllerClass = Class.forName(SIM_CONTROLLER_CLASS, false, classLoader)
        /** Titan 2 MediaTek AntiTheft 最终控制器类。 */
        val antiTheftControllerClass = Class.forName(
            ANTI_THEFT_CONTROLLER_CLASS,
            false,
            classLoader,
        )
        /** 通用数字控制器父类，用于 AntiTheft 脱离回调。 */
        val pinBasedControllerClass = Class.forName(
            PIN_BASED_CONTROLLER_CLASS,
            false,
            classLoader,
        )
        check(pinBasedControllerClass.isAssignableFrom(simControllerClass)) {
            "special_sim_controller_parent"
        }
        check(pinBasedControllerClass.isAssignableFrom(antiTheftControllerClass)) {
            "special_antitheft_controller_parent"
        }
        /** SIM 控制器自己声明的恢复方法。 */
        val simResumeMethod = simControllerClass.getDeclaredMethod(
            CREDENTIAL_RESUME_METHOD,
            Int::class.javaPrimitiveType,
        ).apply {
            check(returnType == Void.TYPE) { "special_sim_resume_signature" }
        }
        /** SIM 控制器自己声明的暂停与脱离方法。 */
        val simPauseMethod = exactVoidMethod(simControllerClass, CREDENTIAL_PAUSE_METHOD)
        /** SIM 控制器最终脱离回调。 */
        val simDetachedMethod = exactVoidMethod(
            simControllerClass,
            CONTROLLER_VIEW_DETACHED_METHOD,
        )
        /** AntiTheft 控制器自己声明的恢复方法。 */
        val antiTheftResumeMethod = antiTheftControllerClass.getDeclaredMethod(
            CREDENTIAL_RESUME_METHOD,
            Int::class.javaPrimitiveType,
        ).apply {
            check(returnType == Void.TYPE) { "special_antitheft_resume_signature" }
        }
        /** AntiTheft 控制器自己声明的暂停方法。 */
        val antiTheftPauseMethod = exactVoidMethod(
            antiTheftControllerClass,
            CREDENTIAL_PAUSE_METHOD,
        )
        /** AntiTheft 使用数字父类声明的脱离回调。 */
        val pinBasedDetachedMethod = exactVoidMethod(
            pinBasedControllerClass,
            CONTROLLER_VIEW_DETACHED_METHOD,
        )

        hook(simResumeMethod)
            .setId(SIM_RESUME_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 原生 SIM 状态机和按键监听器恢复后再建立像素会话。 */
                val result = chain.proceed()
                startSpecialPinSession(chain.thisObject, classLoader)
                result
            })
        hook(simPauseMethod)
            .setId(SIM_PAUSE_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                stopSpecialPinSession(chain.thisObject)
                chain.proceed()
            })
        hook(simDetachedMethod)
            .setId(SIM_DETACHED_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                stopSpecialPinSession(chain.thisObject)
                chain.proceed()
            })
        hook(antiTheftResumeMethod)
            .setId(ANTI_THEFT_RESUME_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 原生防盗服务绑定和提示准备完成后再建立像素会话。 */
                val result = chain.proceed()
                startSpecialPinSession(chain.thisObject, classLoader)
                result
            })
        hook(antiTheftPauseMethod)
            .setId(ANTI_THEFT_PAUSE_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                stopSpecialPinSession(chain.thisObject)
                chain.proceed()
            })
        hook(pinBasedDetachedMethod)
            .setId(ANTI_THEFT_DETACHED_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 父类方法服务多个数字页，只处理最终 AntiTheft 实例。 */
                if (antiTheftControllerClass.isInstance(chain.thisObject)) {
                    stopSpecialPinSession(chain.thisObject)
                }
                chain.proceed()
            })
        log(Log.INFO, LOG_TAG, "special_pin_hooks_installed")
    }

    /** 安装 Titan 2 图案控制器声明的精确恢复、暂停与脱离 Hook。 */
    @SuppressLint("PrivateApi")
    private fun installPatternSecurityHooks(classLoader: ClassLoader) {
        /** Titan 2 图案认证控制器类。 */
        val patternControllerClass = Class.forName(
            PATTERN_CONTROLLER_CLASS,
            false,
            classLoader,
        )
        /** 图案认证页面恢复的方法。 */
        val patternResumeMethod = patternControllerClass.getDeclaredMethod(
            CREDENTIAL_RESUME_METHOD,
            Int::class.javaPrimitiveType,
        )
        check(patternResumeMethod.returnType == Void.TYPE) { "pattern_resume_signature" }
        /** 图案认证页面暂停的方法。 */
        val patternPauseMethod = exactVoidMethod(patternControllerClass, CREDENTIAL_PAUSE_METHOD)
        /** 图案认证页面即将脱离的方法。 */
        val patternDetachedMethod = exactVoidMethod(
            patternControllerClass,
            CONTROLLER_VIEW_DETACHED_METHOD,
        )
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

    /** 安装 Titan 2 PIN 控制器继承链中经过 APK 验证的精确生命周期 Hook。 */
    @SuppressLint("PrivateApi")
    private fun installPinSecurityHooks(classLoader: ClassLoader) {
        /** Titan 2 最终 PIN 控制器类。 */
        val pinControllerClass = Class.forName(PIN_CONTROLLER_CLASS, false, classLoader)
        /** 声明 PIN 恢复方法的数字凭据控制器父类。 */
        val pinBasedControllerClass = Class.forName(
            PIN_BASED_CONTROLLER_CLASS,
            false,
            classLoader,
        )
        /** 声明 PIN 暂停方法的字符凭据控制器父类。 */
        val absKeyInputControllerClass = Class.forName(
            ABS_KEY_INPUT_CONTROLLER_CLASS,
            false,
            classLoader,
        )
        check(pinBasedControllerClass.isAssignableFrom(pinControllerClass)) {
            "pin_controller_parent"
        }
        check(absKeyInputControllerClass.isAssignableFrom(pinControllerClass)) {
            "pin_abs_controller_parent"
        }
        /** PIN 页面恢复时实际分派到的父类方法。 */
        val pinResumeMethod = pinBasedControllerClass.getDeclaredMethod(
            CREDENTIAL_RESUME_METHOD,
            Int::class.javaPrimitiveType,
        )
        check(pinResumeMethod.returnType == Void.TYPE) { "pin_resume_signature" }
        /** PIN 页面暂停时实际分派到的父类方法。 */
        val pinPauseMethod = exactVoidMethod(
            absKeyInputControllerClass,
            CREDENTIAL_PAUSE_METHOD,
        )
        /** 最终 PIN 控制器自己声明的脱离方法。 */
        val pinDetachedMethod = exactVoidMethod(
            pinControllerClass,
            CONTROLLER_VIEW_DETACHED_METHOD,
        )

        hook(pinResumeMethod)
            .setId(PIN_RESUME_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 先完成所有原生恢复逻辑，再只处理最终 PIN 控制器实例。 */
                val result = chain.proceed()
                if (pinControllerClass.isInstance(chain.thisObject)) {
                    startPinSession(chain.thisObject, classLoader)
                }
                result
            })
        hook(pinPauseMethod)
            .setId(PIN_PAUSE_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 父类方法也服务密码页，必须先按最终 PIN 类型过滤。 */
                if (pinControllerClass.isInstance(chain.thisObject)) {
                    stopPinSession(chain.thisObject)
                }
                chain.proceed()
            })
        hook(pinDetachedMethod)
            .setId(PIN_DETACHED_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 最终 PIN 页面重建前再次执行幂等回退。 */
                stopPinSession(chain.thisObject)
                chain.proceed()
            })
        log(Log.INFO, LOG_TAG, "pin_hooks_installed")
    }

    /** 安装 Titan 2 密码控制器及字符凭据父类中经过 APK 验证的精确生命周期 Hook。 */
    @SuppressLint("PrivateApi")
    private fun installPasswordSecurityHooks(classLoader: ClassLoader) {
        /** Titan 2 最终密码控制器类。 */
        val passwordControllerClass = Class.forName(
            PASSWORD_CONTROLLER_CLASS,
            false,
            classLoader,
        )
        /** 声明通用字符凭据校验与限流方法的父控制器类。 */
        val absKeyInputControllerClass = Class.forName(
            ABS_KEY_INPUT_CONTROLLER_CLASS,
            false,
            classLoader,
        )
        check(absKeyInputControllerClass.isAssignableFrom(passwordControllerClass)) {
            "password_abs_controller_parent"
        }
        /** 密码控制器自己声明的页面恢复方法。 */
        val passwordResumeMethod = passwordControllerClass.getDeclaredMethod(
            CREDENTIAL_RESUME_METHOD,
            Int::class.javaPrimitiveType,
        )
        check(passwordResumeMethod.returnType == Void.TYPE) { "password_resume_signature" }
        /** 密码控制器自己声明的页面暂停方法。 */
        val passwordPauseMethod = exactVoidMethod(
            passwordControllerClass,
            CREDENTIAL_PAUSE_METHOD,
        )
        /** 密码控制器自己声明的脱离方法。 */
        val passwordDetachedMethod = exactVoidMethod(
            passwordControllerClass,
            CONTROLLER_VIEW_DETACHED_METHOD,
        )
        /** 密码控制器自己声明的原生状态重置方法。 */
        val passwordResetStateMethod = exactVoidMethod(
            passwordControllerClass,
            PASSWORD_RESET_STATE_METHOD,
        )
        /** 字符凭据父类声明的原生校验入口。 */
        val verifyPasswordMethod = exactVoidMethod(
            absKeyInputControllerClass,
            PASSWORD_VERIFY_METHOD,
        )
        /** 字符凭据父类声明的认证结果处理方法。 */
        val passwordCheckedMethod = absKeyInputControllerClass.getDeclaredMethod(
            PASSWORD_CHECKED_METHOD,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
        check(passwordCheckedMethod.returnType == Void.TYPE) {
            "password_checked_signature"
        }
        /** 字符凭据父类声明的系统限流入口。 */
        val passwordLockoutMethod = absKeyInputControllerClass.getDeclaredMethod(
            PASSWORD_LOCKOUT_METHOD,
            Long::class.javaPrimitiveType,
        )
        check(passwordLockoutMethod.returnType == Void.TYPE) {
            "password_lockout_signature"
        }

        hook(passwordResumeMethod)
            .setId(PASSWORD_RESUME_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 原生页面完成恢复和 IME 准备后再尝试像素接管。 */
                val result = chain.proceed()
                startPasswordSession(chain.thisObject, classLoader)
                result
            })
        hook(passwordPauseMethod)
            .setId(PASSWORD_PAUSE_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 原生暂停逻辑依赖输入框可见性，必须先完整恢复原生绘制。 */
                stopPasswordSession(chain.thisObject)
                chain.proceed()
            })
        hook(passwordDetachedMethod)
            .setId(PASSWORD_DETACHED_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 页面重建前再次执行幂等回退。 */
                stopPasswordSession(chain.thisObject)
                chain.proceed()
            })
        hook(verifyPasswordMethod)
            .setId(PASSWORD_VERIFY_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 只通知最终密码控制器会话，原生方法仍持有和提交唯一凭据对象。 */
                if (passwordControllerClass.isInstance(chain.thisObject)) {
                    notifyPasswordVerificationStarted(chain.thisObject)
                }
                chain.proceed()
            })
        hook(passwordCheckedMethod)
            .setId(PASSWORD_CHECKED_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 先让 SystemUI 完成失败计数、限流或 dismiss，再读取非敏感结果。 */
                val result = chain.proceed()
                if (passwordControllerClass.isInstance(chain.thisObject)) {
                    /** SystemUI 返回的限流毫秒数。 */
                    val timeoutMillis = chain.getArg(1) as Int
                    /** SystemUI 返回的密码匹配结果。 */
                    val matched = chain.getArg(2) as Boolean
                    notifyPasswordChecked(chain.thisObject, timeoutMillis, matched)
                }
                result
            })
        hook(passwordLockoutMethod)
            .setId(PASSWORD_LOCKOUT_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 原生先禁用输入并启动自己的 CountDownTimer，再同步同一截止时间。 */
                val result = chain.proceed()
                if (passwordControllerClass.isInstance(chain.thisObject)) {
                    /** SystemUI 使用的单调时钟锁定截止时间。 */
                    val deadlineElapsedRealtime = chain.getArg(0) as Long
                    notifyPasswordLockoutStarted(chain.thisObject, deadlineElapsedRealtime)
                }
                result
            })
        hook(passwordResetStateMethod)
            .setId(PASSWORD_RESET_STATE_HOOK_ID)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(XposedInterface.Hooker { chain ->
                /** 原生先恢复输入、提示和 IME 条件，再同步非敏感展示状态。 */
                val result = chain.proceed()
                notifyPasswordStateReset(chain.thisObject)
                result
            })
        log(Log.INFO, LOG_TAG, "password_hooks_installed")
    }

    /** 保存当前主安全容器；对象替换时先结束全部旧凭据会话。 */
    private fun attachSecurityController(controller: Any?) {
        if (controller == null) {
            log(Log.ERROR, LOG_TAG, "security_controller_missing")
            return
        }
        if (activeSecurityController === controller) {
            return
        }
        activePatternSession?.dispose()
        activePinSession?.dispose()
        activePasswordSession?.dispose()
        activeSpecialPinSession?.dispose()
        activeSecurityController = controller
        log(Log.INFO, LOG_TAG, "security_controller_attached")
    }

    /** 主安全容器脱离前结束其全部凭据会话并清除对象引用。 */
    private fun detachSecurityController(controller: Any?) {
        if (controller == null || activeSecurityController !== controller) {
            return
        }
        activePatternSession?.dispose()
        activePinSession?.dispose()
        activePasswordSession?.dispose()
        activeSpecialPinSession?.dispose()
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
        activePinSession?.dispose()
        activePasswordSession?.dispose()
        activeSpecialPinSession?.dispose()
        previousSession?.dispose()
        /** 只有通过全部运行时合同才会进入首帧等待的新会话。 */
        val newSession = PixelPatternSecuritySession(
            securityController = securityController,
            patternController = patternController,
            classLoader = classLoader,
            onTakeoverChanged = {
                refreshCredentialTakeoverState()
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

    /** 在原生 PIN 页完成恢复后幂等创建像素认证会话。 */
    private fun startPinSession(pinController: Any?, classLoader: ClassLoader) {
        if (pinController == null) {
            log(Log.ERROR, LOG_TAG, "pin_controller_missing")
            return
        }
        /** 当前已挂载的主安全容器。 */
        val securityController = activeSecurityController ?: run {
            log(Log.WARN, LOG_TAG, "pin_security_controller_unavailable")
            return
        }
        /** 同一原生 PIN 控制器的重复恢复沿用现有会话。 */
        val previousSession = activePinSession
        if (previousSession?.isBoundTo(pinController) == true) {
            log(Log.INFO, LOG_TAG, "pin_session_reused")
            return
        }
        activePatternSession?.dispose()
        activePasswordSession?.dispose()
        activeSpecialPinSession?.dispose()
        previousSession?.dispose()
        /** 只有通过全部运行时合同才会进入首帧等待的新会话。 */
        val newSession = PixelPinSecuritySession(
            securityController = securityController,
            pinController = pinController,
            classLoader = classLoader,
            onTakeoverChanged = {
                refreshCredentialTakeoverState()
            },
            onFailure = { failedSession, throwable ->
                if (activePinSession === failedSession) {
                    log(Log.ERROR, LOG_TAG, "pin_session_runtime_failed", throwable)
                }
            },
            onDisposed = { disposedSession ->
                if (activePinSession === disposedSession) {
                    activePinSession = null
                }
            },
        )
        activePinSession = newSession
        try {
            newSession.start()
            log(Log.INFO, LOG_TAG, "pin_session_started")
        } catch (throwable: Throwable) {
            if (activePinSession === newSession) {
                activePinSession = null
            }
            log(Log.ERROR, LOG_TAG, "pin_session_start_failed", throwable)
        }
    }

    /** 只结束绑定指定原生控制器的 PIN 会话。 */
    private fun stopPinSession(pinController: Any?) {
        if (pinController == null) {
            return
        }
        /** 当前可能属于其他新控制器的 PIN 会话。 */
        val session = activePinSession ?: return
        if (session.isBoundTo(pinController)) {
            session.dispose()
            log(Log.INFO, LOG_TAG, "pin_session_stopped")
        }
    }

    /** 在原生密码页完成恢复后幂等创建像素展示会话。 */
    private fun startPasswordSession(passwordController: Any?, classLoader: ClassLoader) {
        if (passwordController == null) {
            log(Log.ERROR, LOG_TAG, "password_controller_missing")
            return
        }
        /** 当前已挂载的主安全容器。 */
        val securityController = activeSecurityController ?: run {
            log(Log.WARN, LOG_TAG, "password_security_controller_unavailable")
            return
        }
        /** 同一原生密码控制器的重复恢复沿用现有会话。 */
        val previousSession = activePasswordSession
        if (previousSession?.isBoundTo(passwordController) == true) {
            log(Log.INFO, LOG_TAG, "password_session_reused")
            return
        }
        activePatternSession?.dispose()
        activePinSession?.dispose()
        activeSpecialPinSession?.dispose()
        previousSession?.dispose()
        /** 只有通过输入连接、IME、回调和回退合同才会等待像素首帧。 */
        val newSession = PixelPasswordSecuritySession(
            securityController = securityController,
            passwordController = passwordController,
            classLoader = classLoader,
            onTakeoverChanged = {
                refreshCredentialTakeoverState()
            },
            onFailure = { failedSession, throwable ->
                if (activePasswordSession === failedSession) {
                    log(Log.ERROR, LOG_TAG, "password_session_runtime_failed", throwable)
                }
            },
            onDisposed = { disposedSession ->
                if (activePasswordSession === disposedSession) {
                    activePasswordSession = null
                }
            },
        )
        activePasswordSession = newSession
        try {
            newSession.start()
            log(Log.INFO, LOG_TAG, "password_session_started")
        } catch (throwable: Throwable) {
            if (activePasswordSession === newSession) {
                activePasswordSession = null
            }
            log(Log.ERROR, LOG_TAG, "password_session_start_failed", throwable)
        }
    }

    /** 只结束绑定指定原生控制器的密码会话。 */
    private fun stopPasswordSession(passwordController: Any?) {
        if (passwordController == null) {
            return
        }
        /** 当前可能属于其他新控制器的密码会话。 */
        val session = activePasswordSession ?: return
        if (session.isBoundTo(passwordController)) {
            session.dispose()
            log(Log.INFO, LOG_TAG, "password_session_stopped")
        }
    }

    /** 在原生 SIM 或 AntiTheft 页面恢复后幂等创建像素会话。 */
    private fun startSpecialPinSession(specialController: Any?, classLoader: ClassLoader) {
        if (specialController == null) {
            log(Log.ERROR, LOG_TAG, "special_pin_controller_missing")
            return
        }
        /** 当前已挂载的主安全容器。 */
        val securityController = activeSecurityController ?: run {
            log(Log.WARN, LOG_TAG, "special_pin_security_controller_unavailable")
            return
        }
        /** 同一原生特殊控制器的重复恢复沿用现有会话。 */
        val previousSession = activeSpecialPinSession
        if (previousSession?.isBoundTo(specialController) == true) {
            log(Log.INFO, LOG_TAG, "special_pin_session_reused")
            return
        }
        activePatternSession?.dispose()
        activePinSession?.dispose()
        activePasswordSession?.dispose()
        previousSession?.dispose()
        /** 只有精确控件、原生点击链和恢复事务就绪后才等待像素首帧。 */
        val newSession = PixelSpecialPinSecuritySession(
            securityController = securityController,
            specialController = specialController,
            classLoader = classLoader,
            onTakeoverChanged = { refreshCredentialTakeoverState() },
            onFailure = { failedSession, throwable ->
                if (activeSpecialPinSession === failedSession) {
                    log(Log.ERROR, LOG_TAG, "special_pin_session_runtime_failed", throwable)
                }
            },
            onDisposed = { disposedSession ->
                if (activeSpecialPinSession === disposedSession) {
                    activeSpecialPinSession = null
                }
            },
        )
        activeSpecialPinSession = newSession
        try {
            newSession.start()
            log(Log.INFO, LOG_TAG, "special_pin_session_started")
        } catch (throwable: Throwable) {
            if (activeSpecialPinSession === newSession) {
                activeSpecialPinSession = null
            }
            log(Log.ERROR, LOG_TAG, "special_pin_session_start_failed", throwable)
        }
    }

    /** 只结束绑定指定原生 SIM 或 AntiTheft 控制器的会话。 */
    private fun stopSpecialPinSession(specialController: Any?) {
        if (specialController == null) {
            return
        }
        /** 当前可能属于其他新控制器的特殊页会话。 */
        val session = activeSpecialPinSession ?: return
        if (session.isBoundTo(specialController)) {
            session.dispose()
            log(Log.INFO, LOG_TAG, "special_pin_session_stopped")
        }
    }

    /** 向当前同源密码会话发送原生校验开始事件。 */
    private fun notifyPasswordVerificationStarted(passwordController: Any?) {
        /** 当前可能已因页面切换而释放的密码会话。 */
        val session = activePasswordSession ?: return
        if (passwordController != null && session.isBoundTo(passwordController)) {
            session.onVerificationStarted()
        }
    }

    /** 向当前同源密码会话发送原生校验结果。 */
    private fun notifyPasswordChecked(
        passwordController: Any?,
        timeoutMillis: Int,
        matched: Boolean,
    ) {
        /** 当前可能已因成功 dismiss 而释放的密码会话。 */
        val session = activePasswordSession ?: return
        if (passwordController != null && session.isBoundTo(passwordController)) {
            session.onPasswordChecked(timeoutMillis, matched)
        }
    }

    /** 向当前同源密码会话发送原生锁定截止时间。 */
    private fun notifyPasswordLockoutStarted(
        passwordController: Any?,
        deadlineElapsedRealtime: Long,
    ) {
        /** 当前仍在展示的密码会话。 */
        val session = activePasswordSession ?: return
        if (passwordController != null && session.isBoundTo(passwordController)) {
            session.onLockoutStarted(deadlineElapsedRealtime)
        }
    }

    /** 向当前同源密码会话发送原生状态重置事件。 */
    private fun notifyPasswordStateReset(passwordController: Any?) {
        /** 当前仍在展示的密码会话。 */
        val session = activePasswordSession ?: return
        if (passwordController != null && session.isBoundTo(passwordController)) {
            session.onNativeStateReset()
        }
    }

    /** 按所有凭据会话的真实首帧状态统一暂停或恢复普通像素锁屏。 */
    private fun refreshCredentialTakeoverState() {
        /** 当前是否有任一设备凭据页面已经完成像素首帧接管。 */
        val active = activePatternSession?.isTakeoverActive() == true ||
            activePinSession?.isTakeoverActive() == true ||
            activePasswordSession?.isTakeoverActive() == true ||
            activeSpecialPinSession?.isTakeoverActive() == true
        activeSession?.setCredentialTakeoverActive(active)
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

        /** Titan 2 最终 PIN 认证控制器类名。 */
        const val PIN_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPinViewController"

        /** Titan 2 最终密码认证控制器类名。 */
        const val PASSWORD_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPasswordViewController"

        /** Titan 2 MediaTek SIM PIN/PUK/ME 最终控制器类名。 */
        const val SIM_CONTROLLER_CLASS: String =
            "com.mediatek.keyguard.Telephony.KeyguardSimPinPukMeViewController"

        /** Titan 2 MediaTek AntiTheft 最终控制器类名。 */
        const val ANTI_THEFT_CONTROLLER_CLASS: String =
            "com.mediatek.keyguard.AntiTheft.KeyguardAntiTheftLockViewController"

        /** Titan 2 声明 PIN 恢复逻辑的父控制器类名。 */
        const val PIN_BASED_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardPinBasedInputViewController"

        /** Titan 2 声明字符凭据暂停逻辑的父控制器类名。 */
        const val ABS_KEY_INPUT_CONTROLLER_CLASS: String =
            "com.android.keyguard.KeyguardAbsKeyInputViewController"

        /** 控制器视图挂载方法名。 */
        const val CONTROLLER_VIEW_ATTACHED_METHOD: String = "onViewAttached"

        /** 控制器视图脱离方法名。 */
        const val CONTROLLER_VIEW_DETACHED_METHOD: String = "onViewDetached"

        /** 设备凭据页面恢复方法名。 */
        const val CREDENTIAL_RESUME_METHOD: String = "onResume"

        /** 设备凭据页面暂停方法名。 */
        const val CREDENTIAL_PAUSE_METHOD: String = "onPause"

        /** 密码控制器原生状态重置方法名。 */
        const val PASSWORD_RESET_STATE_METHOD: String = "resetState"

        /** 字符凭据父控制器原生校验入口方法名。 */
        const val PASSWORD_VERIFY_METHOD: String = "verifyPasswordAndUnlock"

        /** 字符凭据父控制器原生校验结果方法名。 */
        const val PASSWORD_CHECKED_METHOD: String = "onPasswordChecked"

        /** 字符凭据父控制器原生限流入口方法名。 */
        const val PASSWORD_LOCKOUT_METHOD: String = "handleAttemptLockout"

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

        /** PIN 页面恢复 Hook ID。 */
        const val PIN_RESUME_HOOK_ID: String = "pixel_lockscreen:pin_resume"

        /** PIN 页面暂停 Hook ID。 */
        const val PIN_PAUSE_HOOK_ID: String = "pixel_lockscreen:pin_pause"

        /** PIN 页面脱离 Hook ID。 */
        const val PIN_DETACHED_HOOK_ID: String = "pixel_lockscreen:pin_detached"

        /** 密码页面恢复 Hook ID。 */
        const val PASSWORD_RESUME_HOOK_ID: String = "pixel_lockscreen:password_resume"

        /** 密码页面暂停 Hook ID。 */
        const val PASSWORD_PAUSE_HOOK_ID: String = "pixel_lockscreen:password_pause"

        /** 密码页面脱离 Hook ID。 */
        const val PASSWORD_DETACHED_HOOK_ID: String = "pixel_lockscreen:password_detached"

        /** 密码原生校验开始 Hook ID。 */
        const val PASSWORD_VERIFY_HOOK_ID: String = "pixel_lockscreen:password_verify"

        /** 密码原生校验结果 Hook ID。 */
        const val PASSWORD_CHECKED_HOOK_ID: String = "pixel_lockscreen:password_checked"

        /** 密码原生限流 Hook ID。 */
        const val PASSWORD_LOCKOUT_HOOK_ID: String = "pixel_lockscreen:password_lockout"

        /** 密码原生状态重置 Hook ID。 */
        const val PASSWORD_RESET_STATE_HOOK_ID: String =
            "pixel_lockscreen:password_reset_state"

        /** SIM 页面恢复 Hook ID。 */
        const val SIM_RESUME_HOOK_ID: String = "pixel_lockscreen:sim_resume"

        /** SIM 页面暂停 Hook ID。 */
        const val SIM_PAUSE_HOOK_ID: String = "pixel_lockscreen:sim_pause"

        /** SIM 页面脱离 Hook ID。 */
        const val SIM_DETACHED_HOOK_ID: String = "pixel_lockscreen:sim_detached"

        /** AntiTheft 页面恢复 Hook ID。 */
        const val ANTI_THEFT_RESUME_HOOK_ID: String = "pixel_lockscreen:antitheft_resume"

        /** AntiTheft 页面暂停 Hook ID。 */
        const val ANTI_THEFT_PAUSE_HOOK_ID: String = "pixel_lockscreen:antitheft_pause"

        /** AntiTheft 页面脱离 Hook ID。 */
        const val ANTI_THEFT_DETACHED_HOOK_ID: String =
            "pixel_lockscreen:antitheft_detached"
    }
}
