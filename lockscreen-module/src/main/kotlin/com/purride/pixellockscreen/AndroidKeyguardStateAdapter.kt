package com.purride.pixellockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateFormat
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixellockscreen.ui.LockscreenUiState
import com.purride.pixellockscreen.ui.LockscreenAmbientUiState
import com.purride.pixellockscreen.ui.LockscreenBiometricUiState
import com.purride.pixellockscreen.ui.LockscreenSecurityNoticeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 从 Android 广播中解析的不可变电池状态。 */
internal data class KeyguardBatterySnapshot(
    /** 已限制在 `0..100` 的电量百分比。 */
    val percent: Int,
    /** 设备是否正在充电或已接满电源。 */
    val isCharging: Boolean,
) {
    internal companion object {
        /**
         * 从系统电池广播的 level/scale/status 字段构建安全快照。
         *
         * @param level 当前电量原始值。
         * @param scale 原始电量的最大值。
         * @param status Android 电池状态常量。
         * @return 可直接提交给锁屏 UI 的快照。
         */
        fun from(level: Int, scale: Int, status: Int): KeyguardBatterySnapshot {
            /** 无效原始数据使用下限，避免除零或越界进入 UI。 */
            val percent = if (level >= 0 && scale > 0) {
                ((level.toDouble() / scale.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            } else {
                0
            }
            /** Android 明确报告的两种外接电源状态。 */
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            return KeyguardBatterySnapshot(percent = percent, isCharging = isCharging)
        }
    }
}

/**
 * 把系统时间、日期、电量、充电和明暗配置转换为锁屏渲染状态。
 *
 * 适配器只使用系统广播驱动，不创建计时线程；所有注册和回调都必须位于主线程。
 */
internal class AndroidKeyguardStateAdapter(
    /** SystemUI 进程的应用上下文。 */
    private val context: Context,
    /** 每次完整状态变化时的唯一下游。 */
    private val onStateChanged: (LockscreenUiState, ProductThemeBrightness) -> Unit,
) {
    /** 提供 Android 当前是否允许交互的公开电源状态。 */
    private val powerManager: PowerManager = requireNotNull(
        context.getSystemService(PowerManager::class.java),
    ) { "lockscreen_power_manager" }

    /** 最近一次系统电池广播解析结果。 */
    private var batterySnapshot: KeyguardBatterySnapshot = KeyguardBatterySnapshot(0, false)

    /** 最近一次原生 Keyguard 解析出的非敏感生物识别状态。 */
    private var biometricSnapshot: LockscreenBiometricUiState = LockscreenBiometricUiState()

    /** 最近一次原生 Keyguard 解析出的非敏感信任提示。 */
    private var securityNoticeSnapshot: LockscreenSecurityNoticeUiState =
        LockscreenSecurityNoticeUiState()

    /** 最近一次原生通知栈与媒体播放器的隐私受控摘要。 */
    private var contentSnapshot: Titan2LockscreenContentSnapshot =
        Titan2LockscreenContentSnapshot()

    /** 广播接收器是否已注册，用于保证启停幂等。 */
    private var started: Boolean = false

    /** 只在系统时间、配置或电池事件到来时提交新状态的接收器。 */
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        /** 记录电池变化并重新格式化当前锁屏状态。 */
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                batterySnapshot = intent.toBatterySnapshot()
            }
            emitState()
        }
    }

    /** 注册系统事件并立即提交一帧初始状态。 */
    fun start() {
        checkMainThread()
        if (started) return
        /** 时间、语言、明暗和电池变化的系统事件集。 */
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        /** `ACTION_BATTERY_CHANGED` 注册时返回的粘性初始状态。 */
        val initialBatteryIntent = context.registerReceiver(
            receiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
        started = true
        if (initialBatteryIntent?.action == Intent.ACTION_BATTERY_CHANGED) {
            batterySnapshot = initialBatteryIntent.toBatterySnapshot()
        }
        emitState()
    }

    /** 幂等注销所有系统回调，停止后不再提交状态。 */
    fun stop() {
        checkMainThread()
        if (!started) return
        started = false
        context.unregisterReceiver(receiver)
    }

    /** 原子更新全部只读安全状态；完全相同的快照不触发像素重绘。 */
    fun updateSecurity(snapshot: Titan2VisibleSecuritySnapshot) {
        checkMainThread()
        if (
            biometricSnapshot == snapshot.biometric &&
            securityNoticeSnapshot == snapshot.securityNotice
        ) {
            return
        }
        biometricSnapshot = snapshot.biometric
        securityNoticeSnapshot = snapshot.securityNotice
        emitState()
    }

    /** 更新通知和媒体摘要；完全相同的内容不触发像素重绘。 */
    fun updateContent(snapshot: Titan2LockscreenContentSnapshot) {
        checkMainThread()
        if (contentSnapshot == snapshot) {
            return
        }
        contentSnapshot = snapshot
        emitState()
    }

    /** 格式化当前时间、日期和明暗模式并与电池快照一起提交。 */
    private fun emitState() {
        if (!started) return
        /** 格式化时间使用的当前时刻。 */
        val now = Date()
        /** 跟随用户 12/24 小时设置的时间文本。 */
        val timeText = DateFormat.getTimeFormat(context).format(now)
        /** 当前 SystemUI 配置的首选语言。 */
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        /** 按当前语言生成的完整星期、月份和日期模式。 */
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd")
        /** 按系统语言格式化的锁屏日期。 */
        val dateText = SimpleDateFormat(datePattern, locale).format(now)
        /** 当前系统日间/夜间配置解析结果。 */
        val brightness = when (
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ) {
            Configuration.UI_MODE_NIGHT_YES -> ProductThemeBrightness.DARK
            else -> ProductThemeBrightness.LIGHT
        }
        onStateChanged(
            LockscreenUiState(
                timeText = timeText,
                dateText = dateText,
                batteryPercent = batterySnapshot.percent,
                isCharging = batterySnapshot.isCharging,
                unlockHint = "SWIPE UP TO UNLOCK",
                biometric = biometricSnapshot,
                securityNotice = securityNoticeSnapshot,
                notifications = contentSnapshot.notifications,
                media = contentSnapshot.media,
                quickActions = contentSnapshot.quickActions,
                ambient = lockscreenAmbientUiState(
                    isInteractive = powerManager.isInteractive,
                    epochMillis = now.time,
                ),
            ),
            brightness,
        )
    }

    /** 从当前电池广播提取标准字段。 */
    private fun Intent.toBatterySnapshot(): KeyguardBatterySnapshot = KeyguardBatterySnapshot.from(
        level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
    )

    /** 拒绝从非主线程注册、注销或更新 Android View 状态。 */
    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Keyguard state must run on main thread" }
    }
}

/**
 * 将系统交互状态和当前分钟转换为无定时器的 AOD 防烧屏状态。
 *
 * SystemUI 的分钟广播负责触发重新计算；交互锁屏始终返回零偏移。
 */
internal fun lockscreenAmbientUiState(
    isInteractive: Boolean,
    epochMillis: Long,
): LockscreenAmbientUiState {
    if (isInteractive) {
        return LockscreenAmbientUiState()
    }
    /** 当前 UTC 分钟序号，只用于生成周期性像素偏移。 */
    val minuteIndex = Math.floorDiv(epochMillis, MILLIS_PER_MINUTE)
    /** 水平偏移在五个安全位置之间逐分钟循环。 */
    val horizontalIndex = Math.floorMod(minuteIndex, AMBIENT_BURN_IN_OFFSETS.size.toLong()).toInt()
    /** 垂直偏移使用较慢且错相的循环，避免沿固定对角线往返。 */
    val verticalIndex = Math.floorMod(
        Math.floorDiv(minuteIndex, AMBIENT_BURN_IN_OFFSETS.size.toLong()) + AMBIENT_VERTICAL_PHASE,
        AMBIENT_BURN_IN_OFFSETS.size.toLong(),
    ).toInt()
    return LockscreenAmbientUiState(
        isAmbient = true,
        burnInOffsetX = AMBIENT_BURN_IN_OFFSETS[horizontalIndex],
        burnInOffsetY = AMBIENT_BURN_IN_OFFSETS[verticalIndex],
    )
}

/** AOD 内容允许使用的全部逻辑像素偏移。 */
private val AMBIENT_BURN_IN_OFFSETS: IntArray = intArrayOf(-2, -1, 0, 1, 2)

/** 垂直偏移相对水平偏移使用的固定错相步数。 */
private const val AMBIENT_VERTICAL_PHASE: Long = 2L

/** 防烧屏位置更新使用的一分钟毫秒数。 */
private const val MILLIS_PER_MINUTE: Long = 60_000L
