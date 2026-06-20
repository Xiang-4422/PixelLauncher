package com.purride.pixellauncherv2.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityStatusBarMessageLifecycleTest {

    @Test
    fun statusBarMessageSchedulesAutoClear() {
        val source = mainActivitySource()

        assertTrue(
            "Status bar message must define a clear runnable.",
            source.contains("private val statusBarMessageClearRunnable = Runnable"),
        )
        assertTrue(
            "Status bar messages must schedule automatic clearing after rendering.",
            source.contains("renderCurrentFrame()\n        scheduleStatusBarMessageClear()"),
        )
        assertTrue(
            "A new status bar message must replace the pending clear timeout.",
            source.contains("mainHandler.removeCallbacks(statusBarMessageClearRunnable)\n        mainHandler.postDelayed(statusBarMessageClearRunnable, statusBarMessageTimeoutMs)"),
        )
    }

    @Test
    fun pauseRemovesPendingStatusBarMessageClear() {
        val source = mainActivitySource()

        assertTrue(
            "onPause must remove pending status bar message work with the other UI runnables.",
            source.contains("mainHandler.removeCallbacks(statusBarMessageClearRunnable)"),
        )
        assertTrue(
            "onPause must clear the message so it cannot become permanent after resume.",
            source.contains("state = LauncherStateTransitions.updateStatusBarMessage(state, message = \"\")"),
        )
    }

    private fun mainActivitySource(): String {
        val moduleRoot = resolveModuleRoot()
        return moduleRoot.resolve("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt").readText()
    }

    private fun resolveModuleRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "app") cwd else cwd.resolve("app")
    }
}
