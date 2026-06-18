package com.purride.pixelui.regression

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the SDK boundary: pixel-engine must not depend on PixelLauncher app code.
 */
class BoundaryOwnershipTest {

    @Test
    fun engineMainSourceDoesNotReferenceLauncherAppPackage() {
        val moduleRoot = resolveModuleRoot()
        val offenders = moduleRoot.resolve("src/main/kotlin")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .filter { file -> forbiddenAppPackage in file.readText() }
            .map { file -> file.relativeTo(moduleRoot).invariantSeparatorsPath }
            .toList()

        assertTrue(
            "pixel-engine must not reference app package $forbiddenAppPackage: $offenders",
            offenders.isEmpty(),
        )
    }

    private fun resolveModuleRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "pixel-engine") cwd else cwd.resolve("pixel-engine")
    }

    private companion object {
        const val forbiddenAppPackage = "com.purride.pixellauncherv2"
    }
}
