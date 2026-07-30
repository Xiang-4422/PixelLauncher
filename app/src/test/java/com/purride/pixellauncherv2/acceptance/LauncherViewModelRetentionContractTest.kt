package com.purride.pixellauncherv2.acceptance

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.viewmodel.LauncherViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [LauncherViewModel] 在 [ViewModelStore] 中的 JVM 保留语义契约。
 *
 * 本测试只验证 Activity 配置重建所依赖的状态容器机制，不声称替代真实 ActivityScenario 重建或
 * Android 进程死亡恢复测试。
 */
class LauncherViewModelRetentionContractTest {

    /** 同一个 ViewModelStore 必须返回同一状态持有者及其最新快照。 */
    @Test
    fun retainedViewModelStore_reusesLauncherStateHolder() {
        val retainedStore = ViewModelStore()
        try {
            val originalViewModel = launcherViewModel(retainedStore)
            val drawerState = LauncherState(
                mode = LauncherMode.APP_DRAWER,
                drawerQuery = "WX",
                isLoading = false,
            )
            originalViewModel.current = drawerState

            val retainedViewModel = launcherViewModel(retainedStore)

            assertSame(originalViewModel, retainedViewModel)
            assertEquals(drawerState, retainedViewModel.current)
        } finally {
            retainedStore.clear()
        }
    }

    /** 新的 ViewModelStore 必须创建独立状态持有者，并从默认模型开始。 */
    @Test
    fun newViewModelStore_startsWithIndependentDefaultState() {
        val existingStore = ViewModelStore()
        val newStore = ViewModelStore()
        try {
            val existingViewModel = launcherViewModel(existingStore)
            existingViewModel.current = LauncherState(
                mode = LauncherMode.SETTINGS,
                settingsSelectedIndex = 5,
                isLoading = false,
            )

            val newViewModel = launcherViewModel(newStore)

            assertNotSame(existingViewModel, newViewModel)
            assertEquals(LauncherState(), newViewModel.current)
        } finally {
            existingStore.clear()
            newStore.clear()
        }
    }

    /** 从指定 Store 获取与 Activity 相同类型的 Launcher 状态持有者。 */
    private fun launcherViewModel(store: ViewModelStore): LauncherViewModel =
        ViewModelProvider.create(store)[LauncherViewModel::class]
}
