package com.purride.pixellauncherv2.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.purride.pixellauncherv2.data.CommunicationStatusRepository
import com.purride.pixellauncherv2.data.DeviceStatusRepository
import com.purride.pixellauncherv2.data.FontSettingsRepository
import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 重写中的 Launcher ViewModel（Phase 0）。
 *
 * Phase 0 职责：
 *  - 管理 [DeviceStatusRepository]（电量/充电状态）
 *  - 管理 [CommunicationStatusRepository]（未接来电/未读短信数）
 *  - 从 [FontSettingsRepository] 读取外观与行为设置
 *  - 发布 [StateFlow]<[LauncherUiState]> 供 Activity 订阅
 *
 * Phase 0 不承担渲染，旧 MainActivity 渲染链路保持原状不变。
 * 后续各 Phase 将逐步把 MainActivity 中的仓库调用迁移到此处。
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // 仓库：Phase 0 仅包含无 UI 依赖的三个仓库
    private val deviceStatusRepo = DeviceStatusRepository(context)
    private val communicationRepo = CommunicationStatusRepository(context)
    private val fontSettingsRepo = FontSettingsRepository(context)

    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()

    /** 当前主题颜色方案（由 [state.selectedTheme] 派生）。 */
    val currentTheme: StateFlow<LauncherTheme> = _state
        .map { LauncherThemes.fallbackFrom(it.selectedTheme) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherThemes.fallbackFrom(_state.value.selectedTheme))

    init {
        startDeviceStatus()
        startCommunicationStatus()
        Log.d(TAG, "LauncherViewModel init — initial state: ${ _state.value.let {
            "battery=${it.batteryLevel} theme=${it.selectedTheme} fontStyle=${it.selectedFontStyle}"
        } }")
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    private fun buildInitialState(): LauncherUiState {
        val appearance = fontSettingsRepo.getAppearanceSettings()
        val behavior = fontSettingsRepo.getUiBehaviorSettings()
        return LauncherUiState(
            selectedFontSize = appearance.fontSize,
            selectedFontStyle = appearance.fontStyle,
            selectedPixelShape = appearance.pixelShape,
            selectedDotSizePx = appearance.dotSizePx,
            isPixelGapEnabled = appearance.pixelGapEnabled,
            pixelGapRatio = appearance.pixelGapRatio,
            selectedTheme = appearance.theme,
            drawerListAlignment = behavior.drawerListAlignment,
            openDrawerInSearchMode = behavior.openDrawerInSearchMode,
        )
    }

    // ── Device status ─────────────────────────────────────────────────────────

    private fun startDeviceStatus() {
        deviceStatusRepo.start { status ->
            _state.update { s ->
                s.copy(batteryLevel = status.batteryLevel, isCharging = status.isCharging)
            }
        }
    }

    // ── Communication status ──────────────────────────────────────────────────

    private fun startCommunicationStatus() {
        communicationRepo.start { status ->
            _state.update { s ->
                s.copy(
                    missedCallCount = status.missedCallCount,
                    unreadSmsCount = status.unreadSmsCount,
                )
            }
        }
    }

    // ── Public API (used by MainActivity during Phase 0 bridging) ─────────────

    /**
     * Phase 0 桥接：MainActivity 完成 app 列表加载后调用此函数注入数据。
     * Phase 2（APP_DRAWER 重写）后，app 加载将全部移入 ViewModel。
     */
    fun onAppsLoaded(apps: List<AppEntry>) {
        viewModelScope.launch(Dispatchers.Main) {
            _state.update { s ->
                s.copy(apps = apps, drawerVisibleApps = apps, isLoading = false)
            }
        }
    }

    /**
     * 通用状态变换，供 MainActivity 在迁移期间使用。
     * 逐步替换为具体的语义方法后此函数将被删除。
     */
    fun update(transform: LauncherUiState.() -> LauncherUiState) {
        _state.update { it.transform() }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        deviceStatusRepo.stop()
        communicationRepo.stop()
    }

    companion object {
        private const val TAG = "LauncherViewModel"
    }
}
