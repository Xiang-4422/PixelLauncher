#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The wrapper path is injectable only for tooling contract tests; production defaults to this repository.
GRADLEW_BIN="${PIXEL_GRADLEW_BIN:-$ROOT_DIR/gradlew}"
# Isolated repository avoids resolving a stale mutable artifact from the user's global Maven cache.
COMPATIBILITY_REPOSITORY="$ROOT_DIR/build/compatibility-repository"
cd "$ROOT_DIR"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/pixel-sdk-consumer.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if [[ "${PIXEL_SKIP_COMPATIBILITY_PUBLISH:-0}" != "1" ]]; then
  rm -rf "$COMPATIBILITY_REPOSITORY"
  "$GRADLEW_BIN" \
    :pixel-engine:publishReleasePublicationToCompatibilityRepository \
    --no-daemon
fi

mkdir -p "$TMP_DIR/app/src/main/kotlin/com/purride/pixelsdkconsumer"
mkdir -p "$TMP_DIR/app/src/test/kotlin/com/purride/pixelsdkconsumer"
mkdir -p "$TMP_DIR/app/src/test/java/com/purride/pixelsdkconsumer"

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
        maven {
            name = "PixelCompatibilityRepository"
            url = uri(
                providers.gradleProperty("pixelCompatibilityRepository").orNull
                    ?: error("Missing pixelCompatibilityRepository property."),
            )
            content {
                includeGroup("com.purride")
            }
        }
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

    buildTypes {
        getByName("release") {
            /** 消费者侧开启 R8，验证 SDK 的 consumer rules 与反射入口。 */
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("com.purride:pixel-engine:1.0.0")
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

    override fun onDestroy() {
        setup.dispose()
        super.onDestroy()
    }
}
EOF

cat >"$TMP_DIR/app/src/test/kotlin/com/purride/pixelsdkconsumer/PixelTesterConsumerTest.kt" <<'EOF'
package com.purride.pixelsdkconsumer

import com.purride.pixelui.Text
import com.purride.pixelui.Builder
import com.purride.pixelcore.GlyphBitmap
import com.purride.pixelcore.GlyphMetrics
import com.purride.pixelcore.GlyphProvider
import com.purride.pixelcore.GlyphStyle
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelClusterTextRasterizer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelFontEngine
import com.purride.pixelcore.PixelFontFamily
import com.purride.pixelcore.PixelFontWeight
import com.purride.pixelcore.PixelShape
import com.purride.pixelui.AdaptiveBuilder
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelBackHost
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelHapticFeedback
import com.purride.pixelui.PixelHapticType
import com.purride.pixelui.PixelFrameDropReason
import com.purride.pixelui.PixelFrameTimings
import com.purride.pixelui.PixelFrameWorkload
import com.purride.pixelui.PixelHostFrameDiagnostics
import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.PixelLocalizationBundle
import com.purride.pixelui.PixelLocalizationDelegate
import com.purride.pixelui.PixelLocalizationProvider
import com.purride.pixelui.PixelLocalizationResolver
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelPercentFormatter
import com.purride.pixelui.PixelAdaptiveEnvironment
import com.purride.pixelui.PixelAdaptiveLayoutData
import com.purride.pixelui.PixelHostProfilePolicy
import com.purride.pixelui.Slider
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeBrightness
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.TextDirection
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.PixelWindowOrientation
import com.purride.pixelui.PixelWindowSizeClass
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
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

        // Compatibility toast handle proves passive notifications do not steal application Back.
        val passiveToast = overlay.showToast("SAVED")
        tester.pumpFrame(16)

        assertTrue(tester.exists(find.byText("SDK OK")))
        assertFalse(hapticHandled)
        assertTrue(tester.exists(find.byText("SAVED")))
        assertFalse(backDispatcher.handleBack())
        assertTrue(passiveToast.dismiss())
        tester.pumpFrame(16)
        assertFalse(tester.exists(find.byText("SAVED")))

        // A normal custom popup remains dismissible and exercises the published Back adapter.
        overlay.show(Text("BACK ROUTE"))
        tester.pumpFrame(16)
        assertTrue(tester.exists(find.byText("BACK ROUTE")))
        assertTrue(backDispatcher.handleBack())
        tester.pumpFrame(16)
        assertFalse(tester.exists(find.byText("BACK ROUTE")))
        assertTrue(tester.dumpPixelsAsAscii().startsWith("size=32x8\n"))
        tester.dispose()
    }

    /** Published M5 theme and required-state overloads work from an isolated application module. */
    @Test
    fun canUsePublishedThemeTokensAndStateOverloads() {
        /** Sentinel error color proving semantic roles resolve in the external consumer runtime. */
        val danger = PixelColor.fromRgb(213, 37, 91)
        /** Sentinel loading color proving a second state channel reaches standard components. */
        val loading = PixelColor.fromRgb(229, 173, 31)
        /** Complete custom graph assembled only from published SDK types. */
        val theme = PixelThemeTokens.Light.copy(
            colors = PixelThemeTokens.Light.colors.copy(
                danger = danger,
                warning = loading,
            ),
            labels = PixelLabelTokens.Default.copy(
                checkbox = "External checkbox",
                loading = "External loading",
            ),
        )
        /** Callback count proving Loading removes every public activation path. */
        var checkboxChanges = 0
        /** External off-screen harness consuming the published theme constructor. */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelTheme(
                tokens = theme,
                child = Column(
                    children = listOf(
                        OutlinedButton(
                            text = "DANGER",
                            onPressed = {},
                            states = PixelControlStateSet.of(PixelControlState.Error),
                            key = "external-danger",
                        ),
                        Checkbox(
                            checked = false,
                            onChanged = { checkboxChanges += 1 },
                            states = PixelControlStateSet.of(PixelControlState.Loading),
                            key = "external-loading",
                        ),
                    ),
                ),
            ),
            logicalWidth = 80,
            logicalHeight = 32,
        )

        assertTrue(tester.hasPixel(danger))
        assertTrue(tester.hasPixel(loading))
        /** Localized Loading checkbox semantics exported by the published component overload. */
        val checkboxNode = tester.semanticsNodesByLabel("External checkbox").single()
        assertEquals("External loading", checkboxNode.value)
        assertFalse(checkboxNode.enabled)
        assertFalse(tester.performSemanticsAction(checkboxNode.id, com.purride.pixelui.PixelSemanticsAction.CLICK))
        assertEquals(0, checkboxChanges)
        tester.dispose()
    }

    /** Published Host capability values and inherited accessors work outside the producer project. */
    @Test
    fun canUsePublishedHostCapabilitiesFromKotlin() {
        /** Non-default snapshot proving the isolated consumer resolves the published environment API. */
        val capabilities = HostCapabilitiesData(
            locales = listOf(PixelLocale("zh-Hans-CN"), PixelLocale.English),
            layoutDirection = TextDirection.RTL,
            textScaleFactor = 1.5f,
            highContrast = true,
            density = 2.75f,
            refreshRateHz = 120f,
        )
        /** Snapshot observed through the additive MediaQuery compatibility accessor. */
        var observedCapabilities: HostCapabilitiesData? = null
        /** External off-screen harness proving runtime linkage against the published AAR. */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = HostCapabilities(
                data = capabilities,
                child = Builder { context ->
                    observedCapabilities = MediaQuery.capabilitiesOf(context)
                    Text("HOST OK")
                },
            ),
            logicalWidth = 32,
            logicalHeight = 8,
        )

        assertEquals(capabilities, observedCapabilities)
        assertEquals("zh-Hans-CN", observedCapabilities?.locales?.first()?.languageTag)
        assertEquals(TextDirection.RTL, observedCapabilities?.layoutDirection)
        assertTrue(tester.exists(find.byText("HOST OK")))
        tester.dispose()
    }

    /** Published adaptive environment, profile policies, and contrast helper link externally. */
    @Test
    fun canUsePublishedAdaptiveHostContractsFromKotlin() {
        /** Complete adaptive snapshot with distinguishable physical, logical, and inset values. */
        val adaptiveData = PixelAdaptiveLayoutData(
            physicalWidthPx = 1_080,
            physicalHeightPx = 1_920,
            logicalWidth = 135,
            logicalHeight = 240,
            density = 3f,
            viewInsets = PixelWindowInsets(bottom = 10),
            viewPadding = PixelWindowInsets(top = 2, bottom = 4),
        )
        /** Adaptive policy instantiated solely through the published sealed hierarchy. */
        val profilePolicy: PixelHostProfilePolicy = PixelHostProfilePolicy.AdaptiveDp(
            dotSizeDp = 4f,
            pixelShape = PixelShape.CIRCLE,
        )
        /** Snapshot observed through the published inherited environment and builder. */
        var observedAdaptiveData: PixelAdaptiveLayoutData? = null
        /** External off-screen harness linking every adaptive Widget symbol from the AAR. */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelAdaptiveEnvironment(
                data = adaptiveData,
                child = AdaptiveBuilder(
                    builder = { _, data ->
                        observedAdaptiveData = data
                        Text("ADAPTIVE OK")
                    },
                ),
            ),
            logicalWidth = 32,
            logicalHeight = 8,
        )

        assertEquals(adaptiveData, observedAdaptiveData)
        assertEquals(360f, adaptiveData.widthDp)
        assertEquals(PixelWindowSizeClass.COMPACT, adaptiveData.widthSizeClass)
        assertEquals(PixelWindowOrientation.PORTRAIT, adaptiveData.orientation)
        assertEquals(4f, (profilePolicy as PixelHostProfilePolicy.AdaptiveDp).dotSizeDp)
        assertEquals(
            PixelThemeTokens.HighContrastDark,
            PixelThemeTokens.forCapabilities(
                capabilities = HostCapabilitiesData.Default.copy(highContrast = true),
                brightness = PixelThemeBrightness.Dark,
            ),
        )
        assertTrue(tester.exists(find.byText("ADAPTIVE OK")))
        tester.dispose()
    }

    /** Published localization providers, delegates, and formatters link from an isolated module. */
    @Test
    fun canUsePublishedLocalizationFromKotlin() {
        /** Consumer-owned exact bundle with labels distinct from built-in and theme fallbacks. */
        val bundle = PixelLocalizationBundle(
            locale = PixelLocale("fr-CA"),
            labels = PixelLabelTokens.Default.copy(
                button = "BOUTON EXTERNE",
                slider = "CURSEUR EXTERNE",
            ),
            navigationBar = "BARRE EXTERNE",
            navigationRail = "RAIL EXTERNE",
            percentFormatter = PixelPercentFormatter { fraction -> "P[$fraction]" },
        )
        /** Exact immutable delegate published through the Java- and Kotlin-friendly factory. */
        val delegate = PixelLocalizationDelegate.fromBundles(bundle)
        /** Resolver proving exact custom bundles remain above built-in language fallbacks. */
        val resolver = PixelLocalizationResolver(delegate = delegate)
        /** External runtime renders provider-aware production components from the published AAR. */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelLocalizationProvider(
                resolver = resolver,
                localeOverride = PixelLocale("fr-CA"),
                child = Column(
                    children = listOf(
                        OutlinedButton(text = "", onPressed = {}),
                        Slider(value = 0.5f, onDrag = {}, onRelease = {}),
                    ),
                ),
            ),
            logicalWidth = 96,
            logicalHeight = 32,
        )

        assertEquals(1, tester.semanticsNodesByLabel("BOUTON EXTERNE").size)
        /** Slider proves custom text and percentage formatting cross the artifact boundary. */
        val slider = tester.semanticsNodesByLabel("CURSEUR EXTERNE").single()
        assertEquals("P[0.5]", slider.value)
        tester.dispose()
    }

    /** Published scalar font overloads and cluster capability link from an isolated consumer. */
    @Test
    fun canUsePublishedCodePointAndClusterFontContracts() {
        /** Supplementary scalar whose two UTF-16 units must produce one provider request. */
        val supplementaryCodePoint = 0x1F642
        /** Exact supplementary source payload consumed by the public font engine. */
        val supplementaryText = String(Character.toChars(supplementaryCodePoint))
        /** Complete scalar requests observed through the additive provider overload. */
        val scalarRequests = mutableListOf<Int>()
        /** Distinguishable external style used by the provider and engine. */
        val style = GlyphStyle(
            cellHeight = 4,
            narrowAdvanceWidth = 3,
            wideAdvanceWidth = 6,
            oversampleFactor = 1,
            narrowMinimumSampleRatio = 1f,
            wideMinimumSampleRatio = 1f,
            narrowTextSizeRatio = 1f,
            wideTextSizeRatio = 1f,
            narrowFontWeight = PixelFontWeight.NORMAL,
            wideFontWeight = PixelFontWeight.NORMAL,
            narrowFontFamily = PixelFontFamily.MONOSPACE,
            wideFontFamily = PixelFontFamily.DEFAULT,
        )
        /** Consumer provider implementing both compatibility and complete-scalar entry points. */
        val provider = object : GlyphProvider {
            /** Preserves the required frozen BMP method for old callers. */
            override fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
                return rasterizeGlyph(character.code, style)
            }

            /** Records the complete scalar without narrowing it to a surrogate Char. */
            override fun rasterizeGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap {
                scalarRequests += codePoint
                /** Wide synthetic bitmap proving the supplementary scalar was measured once. */
                val width = style.wideAdvanceWidth
                return GlyphBitmap(
                    width = width,
                    height = style.cellHeight,
                    pixels = ByteArray(width * style.cellHeight) { 1 },
                    metrics = GlyphMetrics(
                        advanceWidth = width,
                        baselineOffset = style.cellHeight - 1,
                        isWideGlyph = true,
                    ),
                )
            }
        }
        /** Public engine under isolated artifact linkage. */
        val engine = PixelFontEngine(provider)
        assertEquals(6, engine.measureText(supplementaryText, style))
        assertEquals(listOf(supplementaryCodePoint), scalarRequests)

        /** Consumer cluster rasterizer advertising only one exact decomposed grapheme. */
        val clusterRasterizer = object : PixelClusterTextRasterizer {
            /** Measures the sample as one atomic consumer glyph. */
            override fun measureText(text: String): Int = if (text.isEmpty()) 0 else 1

            /** Uses one fixed consumer cell row. */
            override fun measureHeight(text: String): Int = 1

            /** Accepts exactly the decomposed cluster owned by the consumer atlas. */
            override fun canRasterizeCluster(cluster: String): Boolean = cluster == "e\u0301"

            /** Paints one marker cell for a supported exact cluster. */
            override fun drawText(
                buffer: PixelBuffer,
                text: String,
                x: Int,
                y: Int,
                color: PixelColor,
            ) {
                if (text.isNotEmpty()) buffer.setPixel(x, y, color)
            }
        }
        assertTrue(clusterRasterizer.canRasterizeCluster("e\u0301"))
        assertFalse(clusterRasterizer.canRasterizeCluster("e"))
    }

    /** Published frame diagnostics retain stable value semantics in an isolated Kotlin consumer. */
    @Test
    fun canUsePublishedFrameDiagnosticsFromKotlin() {
        /** Exclusive phase timings whose sum plus unattributed work equals the total frame. */
        val timings = PixelFrameTimings(
            buildNanos = 10L,
            layoutNanos = 20L,
            paintNanos = 30L,
            bufferSubmitNanos = 40L,
            androidDrawNanos = 50L,
            totalFrameNanos = 160L,
            unattributedNanos = 10L,
        )
        /** Primitive and nullable runtime-pressure counters exported by the stable workload type. */
        val workload = PixelFrameWorkload(
            dirtyElementCount = 2,
            dirtyRenderNodeCount = 3,
            paintedPixelCount = 64L,
            submittedPixelCount = 64L,
            allocatedBytes = 128L,
            garbageCollectionCount = 0L,
            bufferCacheHitCount = 1L,
            bufferCacheMissCount = 0L,
            renderCacheHit = false,
        )
        /** Complete over-budget frame linked only against the isolated published artifact. */
        val diagnostics = PixelHostFrameDiagnostics(
            frameNumber = 7L,
            frameIntervalNanos = 100L,
            frameBudgetNanos = 100L,
            timings = timings,
            workload = workload,
            dropReason = PixelFrameDropReason.ANDROID_DRAW,
            missedVsyncCount = 1,
        )

        assertTrue(diagnostics.isOverBudget)
        assertEquals(40L, diagnostics.timings.bufferSubmitNanos)
        assertEquals(128L, diagnostics.workload.allocatedBytes)
    }
}
EOF

cat >"$TMP_DIR/app/src/test/java/com/purride/pixelsdkconsumer/UnicodeFontJavaConsumerTest.java" <<'EOF'
package com.purride.pixelsdkconsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.purride.pixelcore.GlyphBitmap;
import com.purride.pixelcore.GlyphMetrics;
import com.purride.pixelcore.GlyphProvider;
import com.purride.pixelcore.GlyphSource;
import com.purride.pixelcore.GlyphStyle;
import com.purride.pixelcore.PixelClusterTextRasterizer;
import com.purride.pixelcore.PixelFontEngine;
import com.purride.pixelcore.PixelFontFamily;
import com.purride.pixelcore.PixelFontWeight;
import com.purride.pixelcore.PixelStyledTextRasterizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Verifies Java source compatibility for frozen Char and additive cluster font contracts. */
public final class UnicodeFontJavaConsumerTest {
    /** Exercises Kotlin interface default methods and the cluster capability from Java. */
    @Test
    public void canUsePublishedUnicodeFontContractsFromJava() {
        /** Distinguishable public style passed through both old and additive source methods. */
        final GlyphStyle style = new GlyphStyle(
                4,
                3,
                6,
                1,
                1f,
                1f,
                1f,
                1f,
                PixelFontWeight.NORMAL,
                PixelFontWeight.NORMAL,
                PixelFontFamily.MONOSPACE,
                PixelFontFamily.DEFAULT,
                0
        );
        /** Char-only source representing Java code written before the scalar overload existed. */
        final LegacyCharSource source = new LegacyCharSource();

        assertNull(source.findGlyph((int) 'A', style));
        assertEquals(Character.valueOf('A'), source.requests.get(0));
        assertNull(source.findGlyph(0x1F642, style));
        assertEquals(1, source.requests.size());

        /** Published implementation consumed through the additive cluster interface from Java. */
        final PixelClusterTextRasterizer rasterizer = new PixelStyledTextRasterizer(
                new PixelFontEngine(new MinimalGlyphProvider()),
                style,
                0
        );
        assertTrue(rasterizer.canRasterizeCluster("e"));
        assertFalse(rasterizer.canRasterizeCluster("e\u0301"));
    }

    /** Frozen Char-only source whose inherited scalar method supplies compatibility behavior. */
    private static final class LegacyCharSource implements GlyphSource {
        /** BMP characters forwarded by the inherited scalar compatibility method. */
        private final List<Character> requests = new ArrayList<>();

        /** Records one compatible BMP lookup and deliberately reports a missing glyph. */
        @Override
        public GlyphBitmap findGlyph(char character, GlyphStyle style) {
            requests.add(character);
            return null;
        }
    }

    /** Minimal Java provider used only to construct the published styled rasterizer. */
    private static final class MinimalGlyphProvider implements GlyphProvider {
        /** Returns one deterministic one-column bitmap for any compatible BMP request. */
        @Override
        public GlyphBitmap rasterizeGlyph(char character, GlyphStyle style) {
            /** One-column blank bitmap with valid public metrics. */
            final byte[] pixels = new byte[style.getCellHeight()];
            return new GlyphBitmap(
                    1,
                    style.getCellHeight(),
                    pixels,
                    new GlyphMetrics(1, style.getCellHeight() - 1, false, false, 1, -1)
            );
        }
    }
}
EOF

cat >"$TMP_DIR/app/src/test/java/com/purride/pixelsdkconsumer/HostCapabilitiesJavaConsumerTest.java" <<'EOF'
package com.purride.pixelsdkconsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.purride.pixelui.HostCapabilitiesData;
import com.purride.pixelui.PixelDisplayFeature;
import com.purride.pixelui.PixelDisplayFeatureState;
import com.purride.pixelui.PixelDisplayFeatureType;
import com.purride.pixelui.PixelLocale;
import com.purride.pixelui.PixelLogicalRect;
import com.purride.pixelui.PixelMotionSettings;
import com.purride.pixelui.PixelWindowSizeClass;
import com.purride.pixelui.TextDirection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Verifies Java-visible Host capability signatures from the isolated published AAR. */
public final class HostCapabilitiesJavaConsumerTest {
    /** Builds, reads, copies, and validates the complete platform-neutral capability graph. */
    @Test
    public void canUsePublishedHostCapabilitiesFromJava() {
        /** Canonical locale used as the active Java consumer preference. */
        final PixelLocale locale = new PixelLocale("ar-EG");
        /** Logical hinge bounds proving no Android rectangle leaks into the public constructor. */
        final PixelLogicalRect hingeBounds = new PixelLogicalRect(40f, 0f, 42f, 96f);
        /** Logical feature assembled entirely from published platform-neutral types. */
        final PixelDisplayFeature hinge = new PixelDisplayFeature(
                hingeBounds,
                PixelDisplayFeatureType.HINGE,
                PixelDisplayFeatureState.HALF_OPENED
        );
        /** Immutable locale input used to exercise the full Java constructor descriptor. */
        final List<PixelLocale> locales = Collections.singletonList(locale);
        /** Immutable display-feature input used to exercise the full Java constructor descriptor. */
        final List<PixelDisplayFeature> displayFeatures = Collections.singletonList(hinge);
        /** Complete snapshot linked only against the artifact published to the isolated repository. */
        final HostCapabilitiesData capabilities = new HostCapabilitiesData(
                locales,
                TextDirection.RTL,
                1.75f,
                true,
                new PixelMotionSettings(0.5f, true),
                2.75f,
                Float.valueOf(120f),
                displayFeatures
        );

        assertEquals("ar-EG", capabilities.getLocales().get(0).getLanguageTag());
        assertEquals(TextDirection.RTL, capabilities.getLayoutDirection());
        assertEquals(1.75f, capabilities.getTextScaleFactor(), 0f);
        assertTrue(capabilities.getHighContrast());
        assertEquals(Float.valueOf(120f), capabilities.getRefreshRateHz());
        assertEquals(2f, capabilities.getDisplayFeatures().get(0).getBounds().getWidth(), 0f);
        assertEquals(
                PixelWindowSizeClass.EXPANDED,
                PixelWindowSizeClass.Companion.forWidthDp(840f)
        );

        /** Independent copy proving Java can invoke the stable full copy descriptor. */
        final HostCapabilitiesData copied = capabilities.copy(
                capabilities.getLocales(),
                capabilities.getLayoutDirection(),
                2f,
                capabilities.getHighContrast(),
                capabilities.getMotionSettings(),
                capabilities.getDensity(),
                capabilities.getRefreshRateHz(),
                capabilities.getDisplayFeatures()
        );
        assertEquals(2f, copied.getTextScaleFactor(), 0f);
        assertThrows(
                UnsupportedOperationException.class,
                () -> capabilities.getLocales().add(PixelLocale.Companion.getEnglish())
        );
    }
}
EOF

cat >"$TMP_DIR/app/src/test/java/com/purride/pixelsdkconsumer/PixelLocalizationsJavaConsumerTest.java" <<'EOF'
package com.purride.pixelsdkconsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.purride.pixelui.PixelLabelTokens;
import com.purride.pixelui.PixelLocale;
import com.purride.pixelui.PixelLocalizationBundle;
import com.purride.pixelui.PixelLocalizationDelegate;
import com.purride.pixelui.PixelLocalizationResolver;
import java.util.Collections;
import org.junit.Test;

/** Verifies Java-visible localization signatures from the isolated published AAR. */
public final class PixelLocalizationsJavaConsumerTest {
    /** Builds custom SAM formatters, an exact delegate, and the complete fallback resolver. */
    @Test
    public void canUsePublishedLocalizationFromJava() {
        /** Exact consumer locale represented without a platform Locale dependency. */
        final PixelLocale locale = new PixelLocale("fr-CA");
        /** Consumer bundle proving both formatter SAM interfaces are natural from Java. */
        final PixelLocalizationBundle bundle = new PixelLocalizationBundle(
                locale,
                PixelLabelTokens.Companion.getDefault(),
                "BARRE EXTERNE",
                "RAIL EXTERNE",
                value -> "N[" + value + "]",
                fraction -> "P[" + fraction + "]"
        );
        /** Exact map delegate assembled only through the published static factory. */
        final PixelLocalizationDelegate delegate = PixelLocalizationDelegate.fromBundles(
                Collections.singletonList(bundle)
        );
        /** Full resolver constructor exercises nullable delegate and platform-neutral default locale. */
        final PixelLocalizationResolver resolver = new PixelLocalizationResolver(
                delegate,
                PixelLocale.Companion.getEnglish()
        );

        assertSame(bundle, resolver.resolveBundle(locale));
        assertEquals("N[-9]", bundle.formatInteger(-9));
        assertEquals("P[0.5]", bundle.formatPercent(0.5f));
        assertEquals(
                "PROVIDER",
                PixelLocalizationResolver.resolveText(null, "PROVIDER", "THEME", "ENGLISH")
        );
    }
}
EOF

cat >"$TMP_DIR/app/src/test/java/com/purride/pixelsdkconsumer/FrameDiagnosticsJavaConsumerTest.java" <<'EOF'
package com.purride.pixelsdkconsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.purride.pixelui.PixelFrameDropReason;
import com.purride.pixelui.PixelFrameTimings;
import com.purride.pixelui.PixelFrameWorkload;
import com.purride.pixelui.PixelHostFrameDiagnostics;
import org.junit.Test;

/** Verifies Java-visible full-frame value types from the isolated published AAR. */
public final class FrameDiagnosticsJavaConsumerTest {
    /** Builds and reads every stable timing, workload, and deadline field from Java. */
    @Test
    public void canUsePublishedFrameDiagnosticsFromJava() {
        /** Exclusive timings with a valid total and unattributed remainder. */
        final PixelFrameTimings timings = new PixelFrameTimings(10L, 20L, 30L, 40L, 50L, 160L, 10L);
        /** Frame workload containing process runtime and cache deltas. */
        final PixelFrameWorkload workload = new PixelFrameWorkload(
                2,
                3,
                64L,
                64L,
                Long.valueOf(128L),
                Long.valueOf(0L),
                1L,
                0L,
                false
        );
        /** Complete immutable frame proving nullable enum and nested types remain Java-friendly. */
        final PixelHostFrameDiagnostics diagnostics = new PixelHostFrameDiagnostics(
                7L,
                100L,
                100L,
                timings,
                workload,
                PixelFrameDropReason.ANDROID_DRAW,
                1
        );

        assertTrue(diagnostics.isOverBudget());
        assertEquals(40L, diagnostics.getTimings().getBufferSubmitNanos());
        assertEquals(Long.valueOf(128L), diagnostics.getWorkload().getAllocatedBytes());
    }
}
EOF

"$GRADLEW_BIN" \
  -p "$TMP_DIR" \
  -PpixelCompatibilityRepository="$COMPATIBILITY_REPOSITORY" \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleRelease \
  --no-daemon

echo "SDK consumer smoke passed: $TMP_DIR"
