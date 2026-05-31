package com.purride.pixellauncherv2.viewmodel

import androidx.lifecycle.ViewModel
import com.purride.pixellauncherv2.launcher.LauncherState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the launcher's internal [LauncherState].
 *
 * MainActivity's `state` is a thin delegate onto [current], so every existing
 * `state = transition(state)` reducer call now reads and writes here, and the
 * render path projects [current] to the UI. This replaces the earlier Phase-0
 * scaffold, which kept a *parallel* `LauncherUiState` shadow driven by its own
 * duplicate Device/Communication repositories — that shadow never drove
 * rendering and has been removed; MainActivity's repositories remain the only
 * ones feeding the state.
 */
class LauncherViewModel : ViewModel() {

    private val _state = MutableStateFlow(LauncherState())

    /** Observable state stream for reactive consumers. */
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    /** Current snapshot. The render path and the reducer call sites read/write this. */
    var current: LauncherState
        get() = _state.value
        set(value) {
            _state.value = value
        }
}
