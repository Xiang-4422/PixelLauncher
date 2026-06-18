package com.purride.pixeldemo.scaffold

import com.purride.pixeldemo.catalog.DemoScene

interface DemoNavigator {
    val currentScene: DemoScene
    fun push(scene: DemoScene)
    fun replace(scene: DemoScene)
    fun pop(): Boolean  // 返回 false 表示已在根，由 Activity 决定是否 finish
    fun popToMenu()
}
