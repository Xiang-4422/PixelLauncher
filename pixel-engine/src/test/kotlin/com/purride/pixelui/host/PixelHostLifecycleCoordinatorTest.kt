package com.purride.pixelui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.purride.pixelcore.PixelViewportAlignment
import com.purride.pixelcore.PixelViewportFit
import com.purride.pixelcore.PixelViewportPolicy
import com.purride.pixelcore.PixelViewportQuantization
import com.purride.pixelcore.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host attachment 与 owner lifecycle 正交状态机测试。 */
class PixelHostLifecycleCoordinatorTest {
    /** attach/detach 只改变 attachment 轴，并在 resume 状态下恢复交互。 */
    @Test
    fun repeatedAttachDetachPreservesOwnerStateAndCountsOnlyRealTransitions() {
        val changes = mutableListOf<PixelHostLifecycleDiagnostics>()
        val coordinator = PixelHostLifecycleCoordinator(changes::add)

        coordinator.attach()
        coordinator.attach()
        assertTrue(coordinator.diagnostics().isInteractive)
        coordinator.start()
        coordinator.resume()
        coordinator.detach()
        coordinator.detach()

        val detached = coordinator.diagnostics()
        assertEquals(PixelHostAttachmentState.Detached, detached.attachmentState)
        assertEquals(PixelHostLifecycleState.Resumed, detached.lifecycleState)
        assertFalse(detached.isInteractive)

        coordinator.attach()

        val reattached = coordinator.diagnostics()
        assertEquals(PixelHostAttachmentState.Attached, reattached.attachmentState)
        assertEquals(PixelHostLifecycleState.Resumed, reattached.lifecycleState)
        assertTrue(reattached.isInteractive)
        assertEquals(2L, reattached.attachCount)
        assertEquals(1L, reattached.detachCount)
        assertEquals(2L, reattached.ignoredTransitionCount)
        assertEquals(5, changes.size)
    }

    /** 乱序事件安全收敛，destroy 之后 lifecycle 调用不可复活 Host。 */
    @Test
    fun outOfOrderEventsAreIdempotentAndDestroyIsTerminal() {
        val coordinator = PixelHostLifecycleCoordinator()

        coordinator.resume()
        coordinator.attach()
        coordinator.start()
        coordinator.stop()
        coordinator.pause()
        coordinator.destroy()
        coordinator.destroy()
        coordinator.resume()
        coordinator.attach()

        val diagnostics = coordinator.diagnostics()
        assertEquals(PixelHostLifecycleState.Destroyed, diagnostics.lifecycleState)
        assertEquals(PixelHostAttachmentState.Attached, diagnostics.attachmentState)
        assertFalse(diagnostics.isInteractive)
        assertEquals(1L, diagnostics.resumeCount)
        assertEquals(1L, diagnostics.stopCount)
        assertEquals(0L, diagnostics.pauseCount)
        assertEquals(1L, diagnostics.destroyCount)
        assertEquals(5L, diagnostics.ignoredTransitionCount)
    }

    /** 显式 owner 同步当前状态，并在 ON_DESTROY 前彻底移除 observer。 */
    @Test
    fun explicitLifecycleOwnerDrivesStateAndIsUnboundOnDestroy() {
        val owner = TestLifecycleOwner()
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val coordinator = PixelHostLifecycleCoordinator()

        coordinator.attach()
        coordinator.bindExplicitLifecycleOwner(owner)

        assertEquals(1, owner.registry.observerCount)
        assertEquals(PixelHostLifecycleOwnerBinding.Explicit, coordinator.diagnostics().ownerBinding)
        assertEquals(PixelHostLifecycleState.Resumed, coordinator.diagnostics().lifecycleState)
        assertTrue(coordinator.diagnostics().isInteractive)

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertEquals(PixelHostLifecycleState.Paused, coordinator.diagnostics().lifecycleState)
        assertFalse(coordinator.diagnostics().isInteractive)

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        val destroyed = coordinator.diagnostics()
        assertEquals(PixelHostLifecycleState.Destroyed, destroyed.lifecycleState)
        assertEquals(PixelHostLifecycleOwnerBinding.None, destroyed.ownerBinding)
        assertEquals(0, owner.registry.observerCount)
        assertEquals(1L, destroyed.destroyCount)
    }

    /** 显式 owner 优先于 ViewTree owner，解除后回到 unmanaged 兼容模式。 */
    @Test
    fun explicitOwnerWinsOverViewTreeUntilUnbound() {
        val viewTreeOwner = TestLifecycleOwner().apply {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        val explicitOwner = TestLifecycleOwner().apply {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        val coordinator = PixelHostLifecycleCoordinator()

        coordinator.attach()
        coordinator.updateViewTreeLifecycleOwner(viewTreeOwner)
        coordinator.bindExplicitLifecycleOwner(explicitOwner)
        coordinator.updateViewTreeLifecycleOwner(viewTreeOwner)

        assertEquals(PixelHostLifecycleOwnerBinding.Explicit, coordinator.diagnostics().ownerBinding)
        assertEquals(PixelHostLifecycleState.Resumed, coordinator.diagnostics().lifecycleState)
        assertEquals(0, viewTreeOwner.registry.observerCount)
        assertEquals(1, explicitOwner.registry.observerCount)

        coordinator.unbindLifecycleOwner()

        assertEquals(PixelHostLifecycleOwnerBinding.None, coordinator.diagnostics().ownerBinding)
        assertEquals(PixelHostLifecycleState.Unmanaged, coordinator.diagnostics().lifecycleState)
        assertTrue(coordinator.diagnostics().isInteractive)
        assertEquals(0, explicitOwner.registry.observerCount)
    }

    /** 手动 inset 直接按逻辑像素保存。 */
    @Test
    fun manualInsetsReturnsLogicalInsetsDirectly() {
        val coordinator = PixelHostLifecycleCoordinator()

        val insets = coordinator.manualInsets(left = 1, top = 2, right = 3, bottom = 4)

        assertEquals(PixelWindowInsets(left = 1, top = 2, right = 3, bottom = 4), insets)
    }

    /** canonical 默认策略下，Android 物理 inset 按网格 cell size 向上取整。 */
    @Test
    fun platformInsetsMapToLogicalPixels() {
        val coordinator = PixelHostLifecycleCoordinator()

        val insets = coordinator.platformInsetsToLogical(
            leftPx = 9,
            topPx = 16,
            rightPx = 0,
            bottomPx = 17,
            viewWidth = 80,
            viewHeight = 80,
            screenProfile = ScreenProfile(logicalWidth = 10, logicalHeight = 10, dotSizePx = 8),
            viewportPolicy = PixelViewportPolicy(),
            pixelGapEnabled = false,
            pixelGapRatio = 0f,
        )

        assertEquals(PixelWindowInsets(left = 2, top = 2, right = 0, bottom = 3), insets)
    }

    /** Explicit cover policy converts physical crop and system inset through shared geometry. */
    @Test
    fun explicitViewportPolicyMapsCropIntoLogicalInsets() {
        /** Stateless coordinator hosting the platform-to-logical conversion. */
        val coordinator = PixelHostLifecycleCoordinator()
        /** Top-right integer cover crops four logical columns from the physical left edge. */
        val policy = PixelViewportPolicy(
            fit = PixelViewportFit.COVER,
            quantization = PixelViewportQuantization.INTEGER,
            alignment = PixelViewportAlignment.TOP_RIGHT,
        )

        /** Logical obscuration includes cover crop plus a two-cell right system inset. */
        val insets = coordinator.platformInsetsToLogical(
            leftPx = 0,
            topPx = 0,
            rightPx = 17,
            bottomPx = 0,
            viewWidth = 100,
            viewHeight = 80,
            screenProfile = ScreenProfile(logicalWidth = 10, logicalHeight = 5, dotSizePx = 8),
            viewportPolicy = policy,
            pixelGapEnabled = false,
            pixelGapRatio = 0f,
        )

        assertEquals(PixelWindowInsets(left = 4, top = 0, right = 2, bottom = 0), insets)
    }

    /** Transient IME projection excludes permanent cover crop from unrelated edges. */
    @Test
    fun transientInsetsExcludeViewportCrop() {
        /** Stateless coordinator hosting the physical overlap conversion. */
        val coordinator = PixelHostLifecycleCoordinator()
        /** Top-right cover policy that permanently crops four columns on the left. */
        val policy = PixelViewportPolicy(
            fit = PixelViewportFit.COVER,
            quantization = PixelViewportQuantization.INTEGER,
            alignment = PixelViewportAlignment.TOP_RIGHT,
        )

        /** IME obscures two rows but must not report the unrelated permanent left crop. */
        val insets = coordinator.platformInsetsToLogical(
            leftPx = 0,
            topPx = 0,
            rightPx = 0,
            bottomPx = 32,
            viewWidth = 100,
            viewHeight = 80,
            screenProfile = ScreenProfile(logicalWidth = 10, logicalHeight = 5, dotSizePx = 8),
            viewportPolicy = policy,
            includeViewportCrop = false,
            pixelGapEnabled = false,
            pixelGapRatio = 0f,
        )

        assertEquals(PixelWindowInsets(left = 0, top = 0, right = 0, bottom = 2), insets)
    }

    /** Legacy API 24–29 snapshots preserve bars when no transient IME is present. */
    @Test
    fun legacyInsetsWithoutImeRemainSystemBars() {
        /** Current combined edges matching the stable system-bar baseline. */
        val current = PixelPhysicalInsets(left = 3, top = 24, right = 0, bottom = 48)
        /** Stable navigation and status-bar dimensions. */
        val stable = PixelPhysicalInsets(left = 3, top = 24, right = 0, bottom = 48)

        /** Pure legacy split independent from a platform WindowInsets instance. */
        val split = splitLegacyPlatformInsets(systemWindow = current, stableWindow = stable)

        assertEquals(current, split.systemBars)
        assertEquals(PixelPhysicalInsets.Zero, split.ime)
    }

    /** Legacy API 24–29 snapshots expose the complete larger keyboard edge as IME. */
    @Test
    fun legacyInsetsSeparateImeFromStableNavigationBar() {
        /** Combined edge reported while a 300px keyboard is visible. */
        val current = PixelPhysicalInsets(top = 24, bottom = 300)
        /** Stable status and navigation bars beneath the transient keyboard. */
        val stable = PixelPhysicalInsets(top = 24, bottom = 48)

        /** Split retaining stable bars and complete Type.ime-compatible bottom extent. */
        val split = splitLegacyPlatformInsets(systemWindow = current, stableWindow = stable)

        assertEquals(PixelPhysicalInsets(top = 24, bottom = 48), split.systemBars)
        assertEquals(PixelPhysicalInsets(bottom = 300), split.ime)
    }

    /** Missing stable metadata conservatively keeps current edges out of the IME channel. */
    @Test
    fun legacyInsetsWithoutStableBaselineUseCompatibilityFallback() {
        /** Current bottom edge on a device that reports no stable inset metadata. */
        val current = PixelPhysicalInsets(bottom = 52)

        /** Conservative split avoids inventing keyboard state without a baseline. */
        val split = splitLegacyPlatformInsets(
            systemWindow = current,
            stableWindow = PixelPhysicalInsets.Zero,
        )

        assertEquals(current, split.systemBars)
        assertEquals(PixelPhysicalInsets.Zero, split.ime)
    }

    /** 普通 JVM 测试使用不检查 Android 主线程的 LifecycleRegistry。 */
    private class TestLifecycleOwner : LifecycleOwner {
        /** 可由测试显式推进的 owner registry。 */
        val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle
            get() = registry
    }
}
