package com.purride.pixellauncherv2.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestContractTest {

    @Test
    fun manifestDeclaresNotificationListenerService() {
        val moduleRoot = resolveModuleRoot()
        val manifest = moduleRoot.resolve("src/main/AndroidManifest.xml").readText()

        assertTrue(
            "Data Health LISTENER needs a declared NotificationListenerService.",
            manifest.contains("""android:name=".app.LauncherNotificationListenerService""""),
        )
        assertTrue(
            "Notification listener service must be protected by the system bind permission.",
            manifest.contains("""android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE""""),
        )
        assertTrue(
            "Notification listener service must advertise the platform listener action.",
            manifest.contains("""android:name="android.service.notification.NotificationListenerService""""),
        )
    }

    private fun resolveModuleRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "app") cwd else cwd.resolve("app")
    }
}
