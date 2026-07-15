package com.purride.pixellauncherv2.app

import android.app.Application
import com.purride.pixellauncherv2.data.LegacySensitiveDataCleaner

/** Launcher 进程入口，负责在其他组件启动前执行应用级安全迁移。 */
class PixelLauncherApp : Application() {

    /** 初始化应用，并首先清除旧版本遗留的明文敏感偏好。 */
    override fun onCreate() {
        super.onCreate()
        LegacySensitiveDataCleaner.clear(applicationContext)
    }
}
