package com.purride.pixellauncherv2.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 拨号能力：权限判定与发起通话。
 *
 * 与 [SmsRepository] 的分工一致——[CallLogRepository] 只管通话记录数据，
 * 本仓库负责"打电话"这件事本身。默认电话应用角色（ROLE_DIALER）与通话中
 * 界面在后续阶段接入，届时也落在这里。
 */
class DialerRepository(
    private val context: Context,
) {

    fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 发起通话。号码为空、缺少权限或系统拒绝时返回失败，由调用方决定提示方式。
     *
     * 走 [TelecomManager.placeCall] 而不是 ACTION_CALL：前者是电话栈的正式入口，
     * 后续成为默认电话应用后同一条路径继续可用。
     *
     * 抑制 MissingPermission：权限检查在下面第一段就做了（[hasCallPhonePermission]），
     * 调用方 CallController.callNumber 进来之前还查一次，runCatching 也会把
     * SecurityException 收成 Result.failure。lint 的 PermissionDetector 不做跨方法
     * 分析，看不穿包成 helper 的检查，也认不出 runCatching 在捕获异常。
     */
    @SuppressLint("MissingPermission")
    fun placeCall(number: String): Result<Unit> {
        val trimmed = number.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Number is blank"))
        }
        if (!hasCallPhonePermission()) {
            return Result.failure(SecurityException("Missing CALL_PHONE permission"))
        }
        val telecomManager = context.getSystemService(TelecomManager::class.java)
            ?: return Result.failure(IllegalStateException("TelecomManager unavailable"))
        return runCatching {
            // 用 fromParts 而不是 Uri.parse("tel:$number")：前者会编码 ssp，
            // 号码里的 # 才不会被当成 fragment 分隔符截断。
            telecomManager.placeCall(Uri.fromParts("tel", trimmed, null), null)
        }.onFailure { error ->
            Log.w(LOG_TAG, "placeCall failed", error)
        }
    }

    private companion object {
        const val LOG_TAG = "DialerRepo"
    }
}
