package com.purride.pixelbenchmark

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.benchmark.DeviceInfo
import androidx.benchmark.DeviceMirroring
import androidx.benchmark.Outputs
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/** 延迟暴露与基准 instrumentation 进程关联的唯一 UiDevice。 */
internal object BenchmarkDeviceHolder {
    /** 当前 AndroidJUnitRunner 提供的 instrumentation 实例。 */
    private val instrumentation: Instrumentation by lazy {
        InstrumentationRegistry.getInstrumentation()
    }

    /** AndroidJUnitRunner 注入的设备授权参数，缺少时测试必须在任何 UI 动作前失败。 */
    private val instrumentationArguments: Bundle by lazy {
        InstrumentationRegistry.getArguments()
    }

    /** 当前测试进程中所有共享关键用户旅程使用的设备。 */
    private val device: UiDevice by lazy {
        UiDevice.getInstance(instrumentation)
    }

    /** 用于直接审计活动窗口可访问性树的 UiAutomation 连接。 */
    val uiAutomation: UiAutomation by lazy {
        instrumentation.uiAutomation
    }

    /** 已完成身份校验后唯一允许执行输入动作的设备实例。 */
    @Volatile
    private var authorizedDevice: UiDevice? = null

    /**
     * 校验当前 instrumentation 所在设备与宿主显式授权的硬件序列号一致。
     *
     * 默认拒绝实体设备；只有宿主同时传入允许标志时才可运行真实设备性能采集。
     */
    fun requireAuthorizedDevice(): UiDevice {
        authorizedDevice?.let { authorized -> return authorized }
        return synchronized(this) {
            authorizedDevice ?: validateDeviceBinding().also { authorized ->
                authorizedDevice = authorized
            }
        }
    }

    /**
     * 通过 instrumentation 的 shell 身份执行短命令，并在设备实现不关闭管道时确定性失败。
     *
     * 直接使用 [UiAutomation] 可避免 `UiDevice.executeShellCommand` 在部分 MIUI 版本中更新
     * 可访问性服务标志时无限等待；超时仍会关闭输出管道，不会放宽设备身份校验。
     */
    fun executeShellCommand(command: String): String {
        /** 保存正在读取的输出描述符，便于超时时主动解除阻塞。 */
        val descriptorReference = AtomicReference<ParcelFileDescriptor?>()
        /** 在独立守护线程读取完整标准输出，使调用方能够施加确定性截止时间。 */
        val shellTask = FutureTask {
            /** 当前命令由系统 UiAutomation 以 shell 身份执行并返回只读输出管道。 */
            val descriptor = uiAutomation.executeShellCommand(command)
            descriptorReference.set(descriptor)
            /** AutoCloseInputStream 会在读完或失败时同时关闭底层描述符。 */
            val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader()
                .use { reader -> reader.readText() }
            descriptorReference.compareAndSet(descriptor, null)
            output
        }
        /** 守护线程只服务本次短命令，不应阻止失败的 instrumentation 进程退出。 */
        val shellThread = Thread(shellTask, ShellThreadName).apply {
            isDaemon = true
            start()
        }
        return try {
            shellTask.get(ShellCommandTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (exception: TimeoutException) {
            descriptorReference.getAndSet(null)?.close()
            shellTask.cancel(true)
            error(
                "Pixel benchmark shell command timed out after " +
                    "${ShellCommandTimeoutMillis}ms: $command",
            )
        } finally {
            descriptorReference.getAndSet(null)?.close()
            // 读取已结束时快速回收线程；超时路径由守护线程语义保证不会阻止进程退出。
            shellThread.join(ShellThreadJoinMillis)
        }
    }

    /** 读取只读系统属性并完成序列号与设备类型的双重授权检查。 */
    private fun validateDeviceBinding(): UiDevice {
        Log.d(LogTag, "开始校验 benchmark instrumentation 设备绑定")
        /** 宿主包装脚本从目标设备读取并注入的不可为空硬件序列号。 */
        val expectedHardwareSerial = instrumentationArguments
            .getString(ExpectedHardwareSerialArgument)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: error(
                "Pixel benchmark requires '$ExpectedHardwareSerialArgument'; " +
                    "run it through tools/pixel-connected-benchmark.sh",
            )
        /** 当前 instrumentation 设备通过 shell 身份读取到的真实硬件序列号。 */
        val actualHardwareSerial = executeShellCommand("getprop ro.serialno").trim()
        check(actualHardwareSerial == expectedHardwareSerial) {
            "Pixel benchmark device mismatch: expectedHardwareSerial=$expectedHardwareSerial " +
                "actualHardwareSerial=$actualHardwareSerial"
        }
        Log.d(LogTag, "硬件序列号校验通过")
        /** `ro.kernel.qemu=1` 是 Android 模拟器不依赖型号字符串的稳定身份信号。 */
        val isEmulator = executeShellCommand("getprop ro.kernel.qemu").trim() == "1"
        /** 实体设备必须由宿主为本次调用单独显式授权，不能继承环境默认值。 */
        val allowsPhysicalDevice = instrumentationArguments
            .getString(AllowPhysicalDeviceArgument)
            ?.toBooleanStrictOrNull() == true
        check(isEmulator || allowsPhysicalDevice) {
            "Pixel benchmark rejected physical device $actualHardwareSerial; " +
                "set PIXEL_BENCHMARK_ALLOW_PHYSICAL=1 only for an intentional device run"
        }
        validateDeviceMirroring()
        validateShellOutputAccess()
        Log.d(LogTag, "benchmark instrumentation 设备绑定校验完成")
        return device
    }

    /**
     * 使用有界 shell 管道执行 AndroidX 同等镜像检测，并缓存无镜像结论以避开其文件式 shell 死锁。
     */
    @Suppress("RestrictedApi")
    private fun validateDeviceMirroring() {
        /** SurfaceFlinger 显示清单用于识别 Android Studio 创建的额外投屏显示。 */
        val displayDump = executeShellCommand("dumpsys SurfaceFlinger --displays")
        check(!displayDump.contains(AndroidStudioMirroringDisplayToken)) {
            "Pixel benchmark rejected active Android Studio device mirroring; " +
                "stop mirroring before collecting performance evidence"
        }
        // 已在当前 instrumentation 内完成同等检查，禁止 AndroidX 再走会在部分 MIUI 上死锁的 shell。
        DeviceMirroring.isAndroidStudioDeviceMirroringActiveOverride = false
        Log.d(LogTag, "Android Studio 设备镜像检查通过")
    }

    /**
     * 证明 benchmark 进程写入的输出可由 shell 精确读回，并缓存结论以避开 AndroidX 文件式 shell 死锁。
     *
     * 部分 FUSE 实现会允许 shell 读取 `Android/media` 产物、但拒绝跨 UID 删除文件；发布工具只需
     * 读取并拉取产物，因此这里直接验证真实读取能力，临时文件仍由所属应用进程负责清理。
     */
    @Suppress("RestrictedApi")
    private fun validateShellOutputAccess() {
        /** 使用进程号隔离当前 instrumentation 的一次性 shell 可访问性探针。 */
        val accessProbe = File(
            Outputs.dirUsableByAppAndShell,
            "pixel-shell-access-${android.os.Process.myPid()}.txt",
        )
        try {
            accessProbe.writeText(ShellAccessProbeContent)
            /** shell 精确读回的探针内容，用于排除仅能访问目录但不能读取应用文件的设备。 */
            var shellReadContent = ""
            /** 已执行的读回次数，用于限制外置存储跨进程可见性等待的总时长。 */
            var shellReadAttempt = 0
            while (
                shellReadContent != ShellAccessProbeContent &&
                shellReadAttempt < ShellAccessReadMaxAttempts
            ) {
                if (shellReadAttempt > 0) {
                    // FUSE 可能晚于应用 close 短暂发布新目录项，使用有界退避等待跨 UID 可见。
                    SystemClock.sleep(ShellAccessReadRetryDelayMillis)
                }
                shellReadContent = executeShellCommand(
                    "cat ${shellPathArgument(accessProbe.absolutePath)}",
                )
                shellReadAttempt += 1
            }
            /** 失败时输出固定探针的字节形态，区分权限拒绝与 shell 注入的控制字符。 */
            val shellReadBytes = shellReadContent.toByteArray(Charsets.UTF_8)
            /** 只在读回失败时采集 shell 身份和文件可见性，避免通过空输出猜测根因。 */
            val shellReadDiagnostic = if (shellReadContent != ShellAccessProbeContent) {
                /** instrumentation 启动的命令进程身份。 */
                val shellIdentity = executeShellCommand("id").trim()
                /** shell 视角中的输出目录权限与属主。 */
                val shellDirectoryListing = executeShellCommand(
                    "ls -ld ${shellPathArgument(requireNotNull(accessProbe.parentFile).absolutePath)}",
                ).trim()
                /** shell 视角中的探针文件权限与属主。 */
                val shellFileListing = executeShellCommand(
                    "ls -l ${shellPathArgument(accessProbe.absolutePath)}",
                ).trim()
                listOf(shellIdentity, shellDirectoryListing, shellFileListing).joinToString("|")
            } else {
                "not-needed"
            }
            check(shellReadContent == ShellAccessProbeContent) {
                "Pixel benchmark shell cannot read app-written output: ${accessProbe.absolutePath}; " +
                    "attempts=$shellReadAttempt, actualLength=${shellReadBytes.size}, actualHex=" +
                    shellReadBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) } +
                    ", diagnostic=$shellReadDiagnostic"
            }
            // 已完成写入与精确读回校验，禁止 AndroidX 重复执行可能受 FUSE 删除语义影响的代理探针。
            DeviceInfo.canShellAccessAppFilesOverride = true
            Log.d(LogTag, "benchmark 输出目录 shell 访问检查通过")
        } finally {
            accessProbe.delete()
        }
    }

    /** 校验文件路径不含 `Runtime.exec` 可拆分或解释为额外参数的字符，再原样交给单命令执行。 */
    private fun shellPathArgument(argument: String): String {
        require(argument.isNotEmpty() && argument.all { it.isLetterOrDigit() || it in ShellPathSafeCharacters }) {
            "Pixel benchmark shell path contains unsupported characters: $argument"
        }
        return argument
    }

    /** 宿主与 AndroidJUnitRunner 共享的设备授权参数名。 */
    private const val ExpectedHardwareSerialArgument: String =
        "pixel.benchmark.expectedHardwareSerial"

    /** 宿主与 AndroidJUnitRunner 共享的实体设备显式授权参数名。 */
    private const val AllowPhysicalDeviceArgument: String = "pixel.benchmark.allowPhysical"

    /** 单条设备身份或输入 shell 命令允许占用的最长时间。 */
    private const val ShellCommandTimeoutMillis: Long = 5_000L

    /** 超时清理时等待 shell 守护线程响应关闭信号的短暂上限。 */
    private const val ShellThreadJoinMillis: Long = 200L

    /** 有界 shell 读取线程使用的稳定诊断名称。 */
    private const val ShellThreadName: String = "pixel-benchmark-shell"

    /** 设备绑定阶段写入 logcat 的稳定标签。 */
    private const val LogTag: String = "PixelBenchmarkDevice"

    /** 外置存储读回探针在确定失败前允许执行的最大次数。 */
    private const val ShellAccessReadMaxAttempts: Int = 5

    /** 外置存储读回探针相邻尝试之间的短暂等待时长。 */
    private const val ShellAccessReadRetryDelayMillis: Long = 100L

    /** 可在单个 `Runtime.exec` 路径参数中原样传递的非字母数字字符。 */
    private const val ShellPathSafeCharacters: String = "/._-"

    /** Android Studio 设备镜像在 SurfaceFlinger 显示清单中的官方稳定标记。 */
    private const val AndroidStudioMirroringDisplayToken: String = "studio.screen.sharing"

    /** shell 可访问性探针写入的固定非敏感内容。 */
    private const val ShellAccessProbeContent: String = "pixel-benchmark-shell-access"
}
