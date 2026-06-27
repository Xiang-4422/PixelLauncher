package com.purride.pixeldemo.catalog

import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.Widget
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixeldemo.scaffold.DemoEnv

data class DemoCategory(
    val id: String,
    val title: String,
    val summary: String,
)

data class DemoGroup(
    val id: String,
    val title: String,
    val summary: String,
    val category: DemoCategory,
    val sceneIds: List<String>,
)

interface DemoScene {
    val id: String
    val title: String
    val summary: String
    val description: String get() = summary
    val category: DemoCategory? get() = null
    val tags: Set<String> get() = emptySet()
    val apis: Set<String> get() = emptySet()
    val initialProfile: PixelHostProfilePreference? get() = null
    val pagerGesturePolicy: PagerGesturePolicy? get() = null
    val isFullScreen: Boolean get() = false
    fun build(env: DemoEnv): Widget
}
