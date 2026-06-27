#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-sdk-consumer.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

./gradlew :pixel-engine:publishToMavenLocal --no-daemon

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixelsdkconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixelsdkconsumer"

cat >"$TMP_DIR/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
rootProject.name = "PixelSdkConsumerSmoke"
include(":app")
EOF

cat >"$TMP_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "9.0.1" apply false
}
EOF

cat >"$TMP_DIR/app/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application")
}

android {
    namespace = "com.purride.pixelsdkconsumer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.purride.pixelsdkconsumer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("com.purride:pixel-engine:0.1.0-SNAPSHOT")
    implementation("androidx.activity:activity:1.13.0")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat >"$TMP_DIR/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:theme="@style/AppTheme" android:label="Pixel SDK Consumer">
        <activity android:name=".ConsumerActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

mkdir -p "$TMP_DIR/app/src/main/res/values"
cat >"$TMP_DIR/app/src/main/res/values/styles.xml" <<'EOF'
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.NoActionBar" />
</resources>
EOF

cat >"$TMP_DIR/app/src/main/kotlin/com/purride/pixelsdkconsumer/ConsumerActivity.kt" <<'EOF'
package com.purride.pixelsdkconsumer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.Center
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.Text
import com.purride.pixelui.createPixelHostSetup

class ConsumerActivity : ComponentActivity() {
    private val overlay = PixelOverlayController()
    private val backDispatcher = PixelBackDispatcher()
    private lateinit var setup: PixelHostSetup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setup = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                backDispatcher = backDispatcher,
                onUnhandledBack = {
                    finish()
                    true
                },
                content = {
                    PixelOverlayHost(
                        controller = overlay,
                        child = Center(child = Text("SDK OK")),
                    )
                },
            ),
        )
        setContentView(setup.rootView)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!setup.hostView.handleBackPressed()) {
                        isEnabled = false
                        try {
                            onBackPressedDispatcher.onBackPressed()
                        } finally {
                            isEnabled = true
                        }
                    }
                }
            },
        )
    }
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixelsdkconsumer/PixelTesterConsumerTest.kt" <<'EOF'
package com.purride.pixelsdkconsumer

import com.purride.pixelui.Text
import com.purride.pixelui.Builder
import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelBackHost
import com.purride.pixelui.PixelHapticFeedback
import com.purride.pixelui.PixelHapticType
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTesterConsumerTest {
    @Test
    fun canUsePublishedPixelTester() {
        val tester = PixelTester()
        val overlay = PixelOverlayController()
        val backDispatcher = PixelBackDispatcher()
        var hapticHandled = true

        tester.pumpWidget(
            widget = PixelBackHost(
                dispatcher = backDispatcher,
                child = PixelOverlayHost(
                    controller = overlay,
                    child = Builder { context ->
                        hapticHandled = PixelHapticFeedback.perform(context, PixelHapticType.TAP)
                        Text("SDK OK")
                    },
                ),
            ),
            logicalWidth = 32,
            logicalHeight = 8,
        )

        overlay.showToast("SAVED")
        tester.pumpFrame(16)

        assertTrue(tester.exists(find.byText("SDK OK")))
        assertFalse(hapticHandled)
        assertTrue(tester.exists(find.byText("SAVED")))
        assertTrue(backDispatcher.handleBack())
        tester.pumpFrame(16)
        assertFalse(tester.exists(find.byText("SAVED")))
        assertTrue(tester.dumpPixelsAsAscii().startsWith("size=32x8\n"))
        tester.dispose()
    }
}
EOF

"$ROOT_DIR/gradlew" -p "$TMP_DIR" :app:testDebugUnitTest :app:assembleDebug --no-daemon

echo "SDK consumer smoke passed: $TMP_DIR"
