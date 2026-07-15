package com.purride.pixelui

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.internal.HostRootWidget
import com.purride.pixelui.internal.PixelUiRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host resolution and retained-root integration contract for [HostCapabilitiesData]. */
class PixelHostCapabilitiesIntegrationTest {

    /** Verifies the compatibility default and authoritative override resolution paths. */
    @Test
    fun effectiveResolutionUsesCompatibilityDefaultsUntilAnAtomicOverrideIsProvided() {
        /** Non-default platform motion makes fallback derivation observable. */
        val platformMotion = PixelMotionSettings(animatorDurationScale = 0.5f, reduceMotion = true)
        /** Compatibility snapshot should preserve defaults except for legacy Host controls. */
        val compatibilitySnapshot = resolveEffectiveHostCapabilities(
            capabilitiesOverride = null,
            textDirection = TextDirection.RTL,
            motionSettings = platformMotion,
        )

        assertEquals(
            HostCapabilitiesData.Default.copy(
                layoutDirection = TextDirection.RTL,
                motionSettings = platformMotion,
            ),
            compatibilitySnapshot,
        )

        /** Explicit snapshot must remain authoritative over both legacy fallback inputs. */
        val overrideSnapshot = HostCapabilitiesData(
            layoutDirection = TextDirection.LTR,
            textScaleFactor = 1.75f,
            highContrast = true,
            motionSettings = PixelMotionSettings(animatorDurationScale = 0f),
            density = 3f,
            refreshRateHz = 120f,
        )
        val resolvedOverride = resolveEffectiveHostCapabilities(
            capabilitiesOverride = overrideSnapshot,
            textDirection = TextDirection.RTL,
            motionSettings = platformMotion,
        )

        assertSame(overrideSnapshot, resolvedOverride)
    }

    /** Verifies capability, direction, and motion providers observe one immutable snapshot. */
    @Test
    fun hostRootDerivesDirectionAndMotionFromTheSameCapabilitySnapshot() {
        /** Distinct direction and reduced-motion values expose accidental legacy-field mixing. */
        val capabilities = HostCapabilitiesData(
            layoutDirection = TextDirection.RTL,
            motionSettings = PixelMotionSettings(animatorDurationScale = 0.25f, reduceMotion = true),
        )
        /** Deterministic provider enables PixelMotionScope without Android frame machinery. */
        val tickerProvider = PixelTickerProvider(ManualFrameScheduler())
        /** Retained runtime resolves all three inherited providers exactly as production does. */
        val runtime = PixelUiRuntime()
        var inheritedCapabilities: HostCapabilitiesData? = null
        var inheritedDirection: TextDirection? = null
        var inheritedMotion: PixelMotionSettings? = null
        try {
            runtime.render(
                root = hostRoot(
                    capabilities = capabilities,
                    motionVsync = tickerProvider,
                    child = Builder { context ->
                        inheritedCapabilities = HostCapabilities.of(context)
                        inheritedDirection = Directionality.of(context)
                        inheritedMotion = PixelMotionScope.of(context).settings
                        SizedBox(width = 1, height = 1)
                    },
                ),
                logicalWidth = 8,
                logicalHeight = 8,
            )
        } finally {
            runtime.dispose()
            tickerProvider.dispose()
        }

        assertSame(capabilities, inheritedCapabilities)
        assertEquals(capabilities.layoutDirection, inheritedDirection)
        assertEquals(capabilities.motionSettings, inheritedMotion)
    }

    /** Verifies equal snapshots stay quiet while any distinct capability notifies dependents. */
    @Test
    fun equalCapabilitySnapshotDoesNotNotifyButDistinctSnapshotDoes() {
        /** Renderable leaf is shared so only capability data participates in comparison. */
        val child = SizedBox(width = 1, height = 1)
        /** Existing scope represents the inherited value currently mounted in the tree. */
        val oldScope = HostCapabilities(data = HostCapabilitiesData.Default, child = child)
        /** Equal scope owns a distinct but value-equal defensive snapshot. */
        val equalScope = HostCapabilities(data = HostCapabilitiesData.Default.copy(), child = child)
        /** Changed scope differs in exactly one capability field. */
        val changedScope = HostCapabilities(
            data = HostCapabilitiesData.Default.copy(highContrast = true),
            child = child,
        )

        assertFalse(equalScope.updateShouldNotify(oldScope))
        assertTrue(changedScope.updateShouldNotify(oldScope))
    }

    /**
     * Verifies retained dependency delivery is value-distinct while one identical keyed consumer
     * keeps its State identity across every independently changed capability field.
     */
    @Test
    fun identicalDependentSkipsEqualSnapshotAndRebuildsOncePerCapabilityField() {
        /** Shared allocation sink proving the keyed consumer never creates replacement State. */
        val createdStates = mutableListOf<CapabilityConsumerState>()
        /** Exact Widget instance reused below every fresh declarative Host environment wrapper. */
        val identicalConsumer = CapabilityConsumerWidget(
            createdStates = createdStates,
            key = "distinct-capability-consumer",
        )
        /** Retained runtime exercising the production HostCapabilities inherited reconciliation. */
        val runtime = PixelUiRuntime()
        /** Initial capability value from which every transition changes exactly one next field. */
        val initialSnapshot = HostCapabilitiesData.Default
        /** Ordered single-field transitions covering the complete public capability graph. */
        val transitions = capabilityTransitionsFrom(initialSnapshot)

        try {
            runtime.render(
                root = capabilityScope(capabilities = initialSnapshot, child = identicalConsumer),
                logicalWidth = 8,
                logicalHeight = 8,
            )
            /** Only State allocated during initial mount and retained through all transitions. */
            val retainedState = createdStates.single()

            assertEquals(1, retainedState.buildCount)
            assertSame(initialSnapshot, retainedState.latestCapabilities)

            runtime.render(
                root = capabilityScope(
                    capabilities = initialSnapshot.copy(),
                    child = identicalConsumer,
                ),
                logicalWidth = 8,
                logicalHeight = 8,
            )

            assertEquals("equal snapshot must not rebuild its dependent", 1, retainedState.buildCount)
            assertSame(retainedState, createdStates.single())

            transitions.forEachIndexed { index, transition ->
                runtime.render(
                    root = capabilityScope(
                        capabilities = transition.snapshot,
                        child = identicalConsumer,
                    ),
                    logicalWidth = 8,
                    logicalHeight = 8,
                )

                assertEquals(
                    "${transition.fieldName} must rebuild its dependent exactly once",
                    index + 2,
                    retainedState.buildCount,
                )
                assertEquals(
                    "${transition.fieldName} must publish the complete atomic snapshot",
                    transition.snapshot,
                    retainedState.latestCapabilities,
                )
                assertEquals(1, createdStates.size)
                assertSame(retainedState, createdStates.single())
            }
        } finally {
            runtime.dispose()
        }
    }

    /** Builds the additive capability boundary without unrelated Host environment wrappers. */
    private fun capabilityScope(
        capabilities: HostCapabilitiesData,
        child: Widget,
    ): HostCapabilities {
        return HostCapabilities(
            data = capabilities,
            child = child,
            key = "host-capabilities",
        )
    }

    /** Verifies override changes update dependencies without replacing retained user State. */
    @Test
    fun capabilityOverrideChangePreservesRetainedStateIdentityAndLocalValue() {
        /** Shared sink records every State allocation across fresh declarative child instances. */
        val createdStates = mutableListOf<CapabilityConsumerState>()
        /** Runtime models consecutive PixelHostView frames with a stable host-root key. */
        val runtime = PixelUiRuntime()
        try {
            runtime.render(
                root = hostRoot(
                    HostCapabilitiesData.Default,
                    child = CapabilityConsumerWidget(createdStates, key = "retained-consumer"),
                ),
                logicalWidth = 8,
                logicalHeight = 8,
            )
            /** Local mutation must survive the simultaneous environment and widget update. */
            val originalState = createdStates.single()
            originalState.incrementLocalValue()

            runtime.render(
                root = hostRoot(
                    HostCapabilitiesData.Default.copy(
                        layoutDirection = TextDirection.RTL,
                        textScaleFactor = 2f,
                    ),
                    child = CapabilityConsumerWidget(createdStates, key = "retained-consumer"),
                ),
                logicalWidth = 8,
                logicalHeight = 8,
            )

            assertEquals(1, createdStates.size)
            assertSame(originalState, createdStates.single())
            assertEquals(1, originalState.localValue)
            assertEquals(TextDirection.RTL, originalState.latestCapabilities.layoutDirection)
            assertEquals(2f, originalState.latestCapabilities.textScaleFactor)
        } finally {
            runtime.dispose()
        }
    }

    /** Builds the production Host root with deterministic logical metrics for JVM tests. */
    private fun hostRoot(
        capabilities: HostCapabilitiesData,
        child: Widget,
        motionVsync: PixelTickerProvider? = null,
    ): HostRootWidget {
        return HostRootWidget(
            screenProfile = TestScreenProfile,
            textRasterizer = PixelBitmapFont.Default,
            windowInsets = PixelWindowInsets.Zero,
            viewInsets = PixelWindowInsets.Zero,
            motionVsync = motionVsync,
            capabilities = capabilities,
            child = child,
            key = "host-root",
        )
    }

    /** Builds cumulative transitions where each adjacent snapshot changes exactly one field. */
    private fun capabilityTransitionsFrom(
        initialSnapshot: HostCapabilitiesData,
    ): List<CapabilityTransition> {
        /** Mutable ordered result preserving the field sequence asserted by the acceptance test. */
        val transitions = mutableListOf<CapabilityTransition>()
        /** Previous snapshot used as the base for the next single-field copy. */
        var currentSnapshot = initialSnapshot

        /** Appends one named transition after verifying it differs from its predecessor. */
        fun appendTransition(
            fieldName: String,
            transform: (HostCapabilitiesData) -> HostCapabilitiesData,
        ) {
            /** Snapshot produced by changing only the field named by this transition. */
            val nextSnapshot = transform(currentSnapshot)
            check(nextSnapshot != currentSnapshot) { "$fieldName transition must change its snapshot" }
            transitions += CapabilityTransition(fieldName = fieldName, snapshot = nextSnapshot)
            currentSnapshot = nextSnapshot
        }

        appendTransition(fieldName = "locales") { snapshot ->
            snapshot.copy(locales = listOf(PixelLocale("fr-FR"), PixelLocale.English))
        }
        appendTransition(fieldName = "layoutDirection") { snapshot ->
            snapshot.copy(layoutDirection = TextDirection.RTL)
        }
        appendTransition(fieldName = "textScaleFactor") { snapshot ->
            snapshot.copy(textScaleFactor = 1.5f)
        }
        appendTransition(fieldName = "highContrast") { snapshot ->
            snapshot.copy(highContrast = true)
        }
        appendTransition(fieldName = "motionSettings") { snapshot ->
            snapshot.copy(
                motionSettings = PixelMotionSettings(
                    animatorDurationScale = 0.5f,
                    reduceMotion = true,
                ),
            )
        }
        appendTransition(fieldName = "density") { snapshot ->
            snapshot.copy(density = 2.75f)
        }
        appendTransition(fieldName = "refreshRateHz") { snapshot ->
            snapshot.copy(refreshRateHz = 120f)
        }
        appendTransition(fieldName = "displayFeatures") { snapshot ->
            snapshot.copy(
                displayFeatures = listOf(
                    PixelDisplayFeature(
                        bounds = PixelLogicalRect(left = 40f, top = 0f, right = 42f, bottom = 96f),
                        type = PixelDisplayFeatureType.HINGE,
                        state = PixelDisplayFeatureState.HALF_OPENED,
                    ),
                ),
            )
        }
        return transitions
    }

    /** Stateful capability consumer used to inspect retained dependency behavior. */
    private class CapabilityConsumerWidget(
        /** Allocation sink shared by successive declarative widget instances. */
        private val createdStates: MutableList<CapabilityConsumerState>,
        /** Stable retained identity shared by successive declarative configurations. */
        key: Any,
    ) : StatefulWidget(key = key) {
        /** Allocates and records the only State allowed for one stable retained slot. */
        override fun createState(): State<out StatefulWidget> {
            return CapabilityConsumerState().also(createdStates::add)
        }
    }

    /** Mutable State that subscribes to Host capabilities and retains one local counter. */
    private class CapabilityConsumerState : State<CapabilityConsumerWidget>() {
        /** Number of builds used to distinguish equal from changed inherited snapshots. */
        var buildCount: Int = 0
            private set

        /** User-owned value that must survive capability replacement. */
        var localValue: Int = 0
            private set

        /** Most recent atomic capability snapshot observed during build. */
        lateinit var latestCapabilities: HostCapabilitiesData
            private set

        /** Mutates local State through the public retained scheduling contract. */
        fun incrementLocalValue() {
            setState { localValue += 1 }
        }

        /** Subscribes to Host capabilities and emits a minimal renderable leaf. */
        override fun build(context: BuildContext): Widget {
            buildCount += 1
            latestCapabilities = HostCapabilities.of(context)
            return SizedBox(width = 1, height = 1)
        }
    }

    /** One named atomic snapshot used by the distinct inherited-update acceptance matrix. */
    private data class CapabilityTransition(
        /** Public capability field changed relative to the immediately preceding snapshot. */
        val fieldName: String,
        /** Complete immutable value expected during the dependent's next build. */
        val snapshot: HostCapabilitiesData,
    )

    /** Deterministic fixtures shared by the Host-root integration cases. */
    private companion object {
        /** Stable logical metrics shared by every root transition in this suite. */
        val TestScreenProfile: ScreenProfile = ScreenProfile(
            logicalWidth = 8,
            logicalHeight = 8,
            dotSizePx = 4,
        )
    }
}
