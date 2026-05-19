package com.purride.pixeldemo.scaffold

import com.purride.pixeldemo.catalog.DemoScene

interface DemoNavigator {
    fun push(scene: DemoScene)
    fun popToMenu()
}
