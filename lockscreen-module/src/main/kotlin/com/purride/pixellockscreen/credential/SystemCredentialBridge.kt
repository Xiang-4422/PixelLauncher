package com.purride.pixellockscreen.credential

import android.annotation.SuppressLint
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/** 系统凭据校验返回给像素认证 UI 的脱敏结果。 */
internal sealed interface CredentialCheckResult {
    /** 系统确认凭据匹配。 */
    data object Matched : CredentialCheckResult

    /** 系统确认凭据不匹配，且未要求限流。 */
    data object Rejected : CredentialCheckResult

    /** 系统拒绝继续尝试，并给出限流毫秒数。 */
    data class Throttled(
        /** 系统返回的锁定时长，单位毫秒。 */
        val timeoutMillis: Int,
    ) : CredentialCheckResult

    /** 当前校验因新输入、锁屏退出或生命周期结束而取消。 */
    data object Cancelled : CredentialCheckResult
}

/** 精确系统反射合同的检查结果。 */
internal sealed interface CredentialBridgeContractResult {
    /** Android 15 凭据类与方法签名全部匹配。 */
    data object Ready : CredentialBridgeContractResult

    /** 合同不匹配，必须继续显示原生 Bouncer。 */
    data class Unsupported(
        /** 不包含用户数据的稳定失败原因。 */
        val reasonCode: String,
    ) : CredentialBridgeContractResult
}

/** 可由生命周期安全取消的一次系统凭据校验。 */
internal class PendingCredentialCheck(
    /** 与系统异步任务共享的单次完成门禁。 */
    private val completion: CredentialCheckCompletion,
    /** 调用 Android 异步任务取消方法的动作。 */
    private val cancelSystemTask: () -> Unit,
) : AutoCloseable {
    /** 幂等取消系统任务，并只向上层发送一次取消结果。 */
    fun cancel() {
        if (!completion.complete(CredentialCheckResult.Cancelled)) {
            return
        }
        runCatching(cancelSystemTask)
    }

    /** 生命周期结束等同于取消本次校验。 */
    override fun close() {
        cancel()
    }
}

/** 确保早匹配、最终回调与主动取消中只有第一个结果生效。 */
internal class CredentialCheckCompletion(
    /** 接收脱敏结果的上层回调。 */
    private val callback: (CredentialCheckResult) -> Unit,
    /** 上层回调自身失败时触发原生回退的错误出口。 */
    private val onCallbackFailure: (Throwable) -> Unit,
) {
    /** 是否已经发送终态结果。 */
    private val completed: AtomicBoolean = AtomicBoolean(false)

    /** 首次完成时安全发送结果，重复回调会被忽略。 */
    fun complete(result: CredentialCheckResult): Boolean {
        if (!completed.compareAndSet(false, true)) {
            return false
        }
        runCatching { callback(result) }
            .onFailure { throwable -> runCatching { onCallbackFailure(throwable) } }
        return true
    }
}

/**
 * 把临时像素凭据交给 Android 15 `LockPatternChecker` 的反射桥。
 *
 * 桥不实现任何安全判断。系统入口同步复制凭据后，模块会立即关闭系统凭据对象和输入 lease；
 * 最终匹配、失败与限流结果仍完全由 Android 返回。
 */
internal class SystemCredentialBridge(
    /** 目标 SystemUI 的最终类加载器。 */
    private val classLoader: ClassLoader,
) {
    /** 首次使用时解析并缓存的 Android 15 隐藏 API 精确合同。 */
    private val reflectionContract: CredentialReflectionContract by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        resolveContract()
    }

    /** 验证目标实例和全部隐藏 API 签名，检查失败时不创建任何系统凭据。 */
    fun verifyContract(lockPatternUtils: Any): CredentialBridgeContractResult =
        runCatching {
            /** 当前 Android 15 隐藏 API 的精确反射合同。 */
            val contract = reflectionContract
            check(contract.lockPatternUtilsClass.isInstance(lockPatternUtils)) {
                "lock_pattern_utils_instance"
            }
        }.fold(
            onSuccess = { CredentialBridgeContractResult.Ready },
            onFailure = { throwable ->
                CredentialBridgeContractResult.Unsupported(
                    throwable.message?.takeIf(String::isNotBlank) ?: "credential_contract_resolution",
                )
            },
        )

    /**
     * 启动一次系统校验，并在方法返回前清零模块 lease 与临时系统凭据。
     *
     * @throws IllegalStateException 目标 ROM 签名不匹配时由宿主捕获并恢复原生 Bouncer。
     */
    fun checkCredential(
        lockPatternUtils: Any,
        userId: Int,
        credential: EphemeralCredentialLease,
        onCallbackFailure: (Throwable) -> Unit,
        callback: (CredentialCheckResult) -> Unit,
    ): PendingCredentialCheck {
        /** 解析成功后用于清零系统凭据的反射合同。 */
        var resolvedContract: CredentialReflectionContract? = null
        /** 仅在调用 `checkCredential` 前短暂存在的系统凭据。 */
        var systemCredential: Any? = null
        try {
            /** 当前 Android 15 隐藏 API 的精确反射合同。 */
            val contract = reflectionContract
            resolvedContract = contract
            check(contract.lockPatternUtilsClass.isInstance(lockPatternUtils)) {
                "lock_pattern_utils_instance"
            }
            /** 对多个系统回调和主动取消进行仲裁的单次完成门禁。 */
            val completion = CredentialCheckCompletion(callback, onCallbackFailure)
            /** 接收 Android 校验结果的动态代理。 */
            val callbackProxy = createCallbackProxy(contract.callbackClass, completion)
            systemCredential = createSystemCredential(contract, credential)
            /** Android 返回的异步校验任务。 */
            val systemTask = contract.checkCredentialMethod.invoke(
                null,
                lockPatternUtils,
                systemCredential,
                userId,
                callbackProxy,
            ) ?: error("credential_task_missing")
            /** 系统异步任务继承的公开取消方法。 */
            val cancelMethod = systemTask.javaClass.getMethod(CANCEL_METHOD, Boolean::class.javaPrimitiveType)
            return PendingCredentialCheck(completion) {
                cancelMethod.invoke(systemTask, false)
            }
        } finally {
            /** Android 入口已同步 duplicate，原始对象无需等待异步任务。 */
            systemCredential?.let { value ->
                resolvedContract?.let { contract ->
                    runCatching { contract.closeCredentialMethod.invoke(value) }
                }
            }
            credential.close()
        }
    }

    /** 将临时 lease 同步复制为 Android 的 `LockscreenCredential`。 */
    private fun createSystemCredential(
        contract: CredentialReflectionContract,
        credential: EphemeralCredentialLease,
    ): Any = when (credential) {
        is EphemeralCredentialLease.Characters -> credential.withCharacters { characters ->
            /** PIN 与密码分别对应的系统静态工厂。 */
            val factory = when (credential.mode) {
                PixelCredentialMode.PIN -> contract.createPinMethod
                PixelCredentialMode.PASSWORD -> contract.createPasswordMethod
                PixelCredentialMode.PATTERN -> error("character_pattern_mismatch")
            }
            factory.invoke(null, characters) ?: error("character_credential_missing")
        }

        is EphemeralCredentialLease.Pattern -> createSystemPattern(contract, credential)
    }

    /** 将 0–8 的路径编号同步转换为 Android `Cell` 列表。 */
    private fun createSystemPattern(
        contract: CredentialReflectionContract,
        credential: EphemeralCredentialLease.Pattern,
    ): Any {
        /** 只在系统工厂调用期间存在的 Cell 列表。 */
        val cells = ArrayList<Any>(credential.size)
        try {
            repeat(credential.size) { index ->
                /** 当前九宫格编号。 */
                val cellId = credential.cellAt(index)
                /** 由编号计算的行。 */
                val row = cellId / PATTERN_SIDE_LENGTH
                /** 由编号计算的列。 */
                val column = cellId % PATTERN_SIDE_LENGTH
                /** Android 内部不可变 Cell 对象。 */
                val cell = contract.createCellMethod.invoke(null, row, column)
                    ?: error("pattern_cell_missing")
                cells += cell
            }
            return contract.createPatternMethod.invoke(null, cells)
                ?: error("pattern_credential_missing")
        } finally {
            cells.clear()
        }
    }

    /** 创建只输出脱敏终态的 Android 回调代理。 */
    private fun createCallbackProxy(
        callbackClass: Class<*>,
        completion: CredentialCheckCompletion,
    ): Any = Proxy.newProxyInstance(classLoader, arrayOf(callbackClass)) { proxy, method, arguments ->
        when (method.name) {
            CALLBACK_EARLY_MATCHED -> completion.complete(CredentialCheckResult.Matched)
            CALLBACK_CHECKED -> {
                /** Android 返回的匹配状态。 */
                val matched = arguments?.getOrNull(0) as? Boolean ?: false
                /** Android 返回的限流毫秒数。 */
                val timeoutMillis = arguments?.getOrNull(1) as? Int ?: 0
                /** 映射后的脱敏终态。 */
                val result = when {
                    matched -> CredentialCheckResult.Matched
                    timeoutMillis > 0 -> CredentialCheckResult.Throttled(timeoutMillis)
                    else -> CredentialCheckResult.Rejected
                }
                completion.complete(result)
            }

            CALLBACK_CANCELLED -> completion.complete(CredentialCheckResult.Cancelled)
            "toString" -> CALLBACK_REDACTED_TEXT
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.getOrNull(0)
        }
        null
    }

    /** 解析并验证 Android 15 `LockPatternChecker` 使用的全部精确方法签名。 */
    @SuppressLint("BlockedPrivateApi", "PrivateApi")
    private fun resolveContract(): CredentialReflectionContract {
        /** Android 内部可清零凭据类。 */
        val credentialClass = Class.forName(LOCKSCREEN_CREDENTIAL_CLASS, false, classLoader)
        /** Android 锁屏配置与校验状态入口。 */
        val lockPatternUtilsClass = Class.forName(LOCK_PATTERN_UTILS_CLASS, false, classLoader)
        /** Android 异步凭据校验器。 */
        val checkerClass = Class.forName(LOCK_PATTERN_CHECKER_CLASS, false, classLoader)
        /** Android 校验结果回调。 */
        val callbackClass = Class.forName(ON_CHECK_CALLBACK_CLASS, false, classLoader)
        /** Android 九宫格 Cell。 */
        val cellClass = Class.forName(PATTERN_CELL_CLASS, false, classLoader)
        /** 创建 PIN 凭据的方法。 */
        val createPinMethod = credentialClass.getDeclaredMethod(CREATE_PIN_METHOD, CharSequence::class.java)
        /** 创建密码凭据的方法。 */
        val createPasswordMethod = credentialClass.getDeclaredMethod(
            CREATE_PASSWORD_METHOD,
            CharSequence::class.java,
        )
        /** 创建图案凭据的方法。 */
        val createPatternMethod = credentialClass.getDeclaredMethod(CREATE_PATTERN_METHOD, List::class.java)
        /** 创建九宫格 Cell 的方法。 */
        val createCellMethod = cellClass.getDeclaredMethod(
            CREATE_CELL_METHOD,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        /** 启动异步校验的方法。 */
        val checkCredentialMethod = checkerClass.getDeclaredMethod(
            CHECK_CREDENTIAL_METHOD,
            lockPatternUtilsClass,
            credentialClass,
            Int::class.javaPrimitiveType,
            callbackClass,
        )
        /** 主动清零系统凭据的方法。 */
        val closeCredentialMethod = credentialClass.getMethod(CLOSE_METHOD)
        check(callbackClass.isInterface) { "credential_callback_interface" }
        return CredentialReflectionContract(
            lockPatternUtilsClass = lockPatternUtilsClass,
            callbackClass = callbackClass,
            createPinMethod = createPinMethod,
            createPasswordMethod = createPasswordMethod,
            createPatternMethod = createPatternMethod,
            createCellMethod = createCellMethod,
            checkCredentialMethod = checkCredentialMethod,
            closeCredentialMethod = closeCredentialMethod,
        )
    }

    /** 汇总一次解析所需的反射类型与方法，不持有任何用户或 View 对象。 */
    private data class CredentialReflectionContract(
        /** Android `LockPatternUtils` 类型。 */
        val lockPatternUtilsClass: Class<*>,
        /** Android `OnCheckCallback` 接口。 */
        val callbackClass: Class<*>,
        /** PIN 工厂。 */
        val createPinMethod: Method,
        /** 密码工厂。 */
        val createPasswordMethod: Method,
        /** 图案工厂。 */
        val createPatternMethod: Method,
        /** 九宫格 Cell 工厂。 */
        val createCellMethod: Method,
        /** 异步校验入口。 */
        val checkCredentialMethod: Method,
        /** 系统凭据清零入口。 */
        val closeCredentialMethod: Method,
    )

    private companion object {
        /** 九宫格单边格子数。 */
        const val PATTERN_SIDE_LENGTH: Int = 3

        /** Android 内部凭据类名。 */
        const val LOCKSCREEN_CREDENTIAL_CLASS: String =
            "com.android.internal.widget.LockscreenCredential"

        /** Android 锁屏工具类名。 */
        const val LOCK_PATTERN_UTILS_CLASS: String = "com.android.internal.widget.LockPatternUtils"

        /** Android 异步校验器类名。 */
        const val LOCK_PATTERN_CHECKER_CLASS: String = "com.android.internal.widget.LockPatternChecker"

        /** Android 校验回调类名。 */
        const val ON_CHECK_CALLBACK_CLASS: String =
            "com.android.internal.widget.LockPatternChecker\$OnCheckCallback"

        /** Android 图案 Cell 类名。 */
        const val PATTERN_CELL_CLASS: String = "com.android.internal.widget.LockPatternView\$Cell"

        /** 创建 PIN 的方法名。 */
        const val CREATE_PIN_METHOD: String = "createPin"

        /** 创建密码的方法名。 */
        const val CREATE_PASSWORD_METHOD: String = "createPassword"

        /** 创建图案的方法名。 */
        const val CREATE_PATTERN_METHOD: String = "createPattern"

        /** 创建 Cell 的方法名。 */
        const val CREATE_CELL_METHOD: String = "of"

        /** 系统异步校验方法名。 */
        const val CHECK_CREDENTIAL_METHOD: String = "checkCredential"

        /** 异步任务取消方法名。 */
        const val CANCEL_METHOD: String = "cancel"

        /** 系统凭据清零方法名。 */
        const val CLOSE_METHOD: String = "close"

        /** 早匹配回调名。 */
        const val CALLBACK_EARLY_MATCHED: String = "onEarlyMatched"

        /** 最终校验回调名。 */
        const val CALLBACK_CHECKED: String = "onChecked"

        /** 取消回调名。 */
        const val CALLBACK_CANCELLED: String = "onCancelled"

        /** 动态代理字符串化时的固定脱敏文本。 */
        const val CALLBACK_REDACTED_TEXT: String = "[REDACTED_CREDENTIAL_CALLBACK]"
    }
}
