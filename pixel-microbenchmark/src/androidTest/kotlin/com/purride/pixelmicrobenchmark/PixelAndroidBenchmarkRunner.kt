package com.purride.pixelmicrobenchmark

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.benchmark.junit4.AndroidBenchmarkRunner

/**
 * 为 Pixel Microbenchmark 保留 AndroidX 隔离窗口，并适配 MIUI 的后台 Activity 启动限制。
 *
 * AOSP 会把 instrumentation 视为允许启动 Activity 的测试进程；MIUI 12.5 额外使用自定义
 * AppOp 10021 阻止同包的 [androidx.benchmark.IsolationActivity]。该 runner 只修改即将由
 * UTP 卸载的测试包权限，不修改生产应用、用户应用或 benchmark 错误抑制策略。
 */
public class PixelAndroidBenchmarkRunner : AndroidBenchmarkRunner() {
    /** 在 AndroidX runner 进入测试生命周期前准备厂商专用的隔离 Activity 启动条件。 */
    override fun onStart() {
        configureMiuiBackgroundActivityStart()
        super.onStart()
    }

    /** 仅在 Xiaomi 系统上允许当前测试包执行 AndroidX 隔离 Activity 的后台启动。 */
    private fun configureMiuiBackgroundActivityStart() {
        if (!Build.MANUFACTURER.equals(XIAOMI_MANUFACTURER, ignoreCase = true)) {
            return
        }
        // testPackageName 来自已安装 instrumentation 目标，字符集受 Android 包名规则约束。
        val testPackageName = targetContext.packageName
        executeShellCommand("cmd appops set $testPackageName $MIUI_BACKGROUND_START_ACTIVITY_OP allow")
        // appOpsState 必须不再包含 MIUI 新安装测试包的默认 ignore 状态。
        val appOpsState = executeShellCommand("cmd appops get $testPackageName")
        check(!appOpsState.contains(MIUI_BACKGROUND_START_ACTIVITY_DENIED_STATE)) {
            "MIUI 未允许测试包启动 AndroidX IsolationActivity：$testPackageName"
        }
    }

    /** 以 instrumentation UiAutomation 的 Shell 身份同步执行命令并读取完整标准输出。 */
    private fun executeShellCommand(command: String): String {
        // outputDescriptor 由 UiAutomation 管理远端命令管道，读取结束后必须关闭。
        val outputDescriptor = uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(outputDescriptor)
            .bufferedReader(Charsets.UTF_8)
            .use { reader -> reader.readText() }
    }

    private companion object {
        /** Android Build 中 Xiaomi/Redmi/POCO ROM 共享的 manufacturer 值。 */
        const val XIAOMI_MANUFACTURER: String = "Xiaomi"

        /** MIUI 12.5 用于“后台弹出界面”的自定义 AppOp 编号。 */
        const val MIUI_BACKGROUND_START_ACTIVITY_OP: Int = 10021

        /** 新安装测试包在未授权时由 `cmd appops get` 返回的拒绝状态。 */
        const val MIUI_BACKGROUND_START_ACTIVITY_DENIED_STATE: String = "MIUIOP(10021): ignore"
    }
}
