package com.purride.pixelui.widgets.animated

import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Opacity
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.VoidCallback
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration

/**
 * 执行 `AnimatedSwitcher` 的 `AnimatedSwitcher` 公开行为；具体参数、返回和副作用见下文。
 *
 * Cross-fades between independently retained keyed child subtrees.
 *
 * A child with the same runtime type and key updates the current retained subtree without starting
 * a transition. A different type or key immediately mounts a new incoming subtree while every
 * visually present outgoing subtree remains mounted until its opacity reaches zero.
 */
public fun AnimatedSwitcher(
    duration: Duration,
    vsync: PixelTickerProvider,
    curve: Curve = Curves.Step(8),
    key: Any? = null,
    child: Widget,
): Widget = AnimatedSwitcherWidget(
    duration = duration,
    vsync = vsync,
    curve = curve,
    child = child,
    key = key,
)

/** Immutable public-call configuration retained by the stateful switcher implementation. */
private class AnimatedSwitcherWidget(
    /** Duration of each uninterrupted cross-fade segment. */
    val duration: Duration,
    /** Host-owned ticker provider driving this switcher. */
    val vsync: PixelTickerProvider,
    /** Curve applied to the controller's normalized progress. */
    val curve: Curve,
    /** Latest requested child configuration. */
    val child: Widget,
    /** Stable switcher identity used by retained reconciliation. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the state that owns visual entries and exactly one animation controller. */
    override fun createState(): State<out StatefulWidget> = AnimatedSwitcherState()
}

/**
 * One independently retained visual child entry.
 *
 * @property id State-local identity used only by the stable opacity wrapper key.
 * @property child Current widget configuration mounted inside this entry.
 * @property startOpacity Raw opacity captured when the current segment began.
 */
private data class AnimatedSwitcherEntry(
    val id: Long,
    val child: Widget,
    val startOpacity: Float,
)

/** Unique sibling key that prevents outgoing and incoming wrappers from sharing one Element. */
private data class AnimatedSwitcherEntryKey(
    /** State-local visual entry identifier. */
    val entryId: Long,
)

/** Stable key preserving the switcher's Stack element across idle and animated frames. */
private object AnimatedSwitcherStackKey

/** State that preserves every visible child subtree across cross-fades and interruptions. */
private class AnimatedSwitcherState : State<AnimatedSwitcherWidget>() {
    /** Single controller whose ticker is owned and disposed by this switcher. */
    private lateinit var controller: PixelAnimationController

    /** Curved view of [controller] used by every entry in the active segment. */
    private lateinit var curved: CurvedAnimation

    /** Entries fading toward zero, ordered from oldest to newest below the incoming entry. */
    private val outgoingEntries: MutableList<AnimatedSwitcherEntry> = mutableListOf()

    /** Foreground target entry, or `null` only after terminal state disposal. */
    private var incomingEntry: AnimatedSwitcherEntry? = null

    /** Monotonic state-local identifier allocated to new visual entries. */
    private var nextEntryId: Long = 0L

    /** Whether [controller] currently drives a cross-fade segment. */
    private var isTransitioning: Boolean = false

    /** Stable listener removed before controller disposal to avoid retaining this State. */
    private val controllerListener: VoidCallback = VoidCallback { handleControllerTick() }

    /** Creates the initial fully opaque entry and an idle controller. */
    override fun initState() {
        incomingEntry = createEntry(child = widget.child, startOpacity = 1f)
        controller = PixelAnimationController(
            duration = widget.duration,
            vsync = widget.vsync,
        )
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        controller.addListener(controllerListener)
        controller.setValue(1f)
    }

    /**
     * Updates compatible children in place or starts an interruption-safe cross-fade.
     *
     * Compatibility matches retained Element semantics: both runtime type and key must match.
     */
    override fun didUpdateWidget(oldWidget: AnimatedSwitcherWidget) {
        val currentIncoming = checkNotNull(incomingEntry)
        if (canUpdate(currentIncoming.child, widget.child)) {
            incomingEntry = currentIncoming.copy(child = widget.child)
            return
        }
        startTransition(nextChild = widget.child)
    }

    /**
     * Captures current visual opacities before targeting [nextChild].
     *
     * Rapid replacement never jumps back to the beginning: all existing outgoing entries and the
     * previous incoming entry continue from their current raw opacity. If [nextChild] matches an
     * outgoing entry, that exact keyed subtree is promoted back to incoming instead of remounting.
     */
    private fun startTransition(nextChild: Widget) {
        // Current curved progress resolves the exact visual state of the interrupted segment.
        val progress = transitionProgress()
        // Snapshots freeze every entry at its current raw opacity before controller restart.
        val snapshots = buildList {
            outgoingEntries.forEach { entry ->
                add(entry.copy(startOpacity = outgoingOpacity(entry, progress)))
            }
            val currentIncoming = checkNotNull(incomingEntry)
            add(currentIncoming.copy(startOpacity = incomingOpacity(currentIncoming, progress)))
        }
        // Reversing to an outgoing identity preserves that entry's Element and State subtree.
        val promotedIndex = snapshots.indexOfLast { entry -> canUpdate(entry.child, nextChild) }
        // Promoted entry retains its wrapper ID; a genuinely new child receives a fresh ID.
        val nextIncoming = if (promotedIndex >= 0) {
            snapshots[promotedIndex].copy(child = nextChild)
        } else {
            createEntry(child = nextChild, startOpacity = 0f)
        }

        outgoingEntries.clear()
        snapshots.forEachIndexed { index, entry ->
            if (index != promotedIndex && entry.startOpacity > 0f) {
                outgoingEntries += entry
            }
        }
        incomingEntry = nextIncoming
        isTransitioning = true
        controller.forward(from = 0f)
    }

    /** Removes all completed outgoing entries so retained reconciliation unmounts them once. */
    private fun handleControllerTick() {
        if (!isTransitioning || controller.status != PixelAnimationStatus.Completed) return
        setState {
            outgoingEntries.clear()
            incomingEntry = checkNotNull(incomingEntry).copy(startOpacity = 1f)
            isTransitioning = false
        }
    }

    /** Releases the controller ticker and state-owned child configuration references. */
    override fun dispose() {
        controller.removeListener(controllerListener)
        controller.dispose()
        outgoingEntries.clear()
        incomingEntry = null
    }

    /** Builds a stable keyed Stack so incoming child State never remounts at completion. */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        // Idle frames resolve to one fully opaque incoming entry; active frames use curved progress.
        val progress = transitionProgress()
        // Outgoing entries paint below the incoming target in deterministic age order.
        val children = buildList {
            outgoingEntries.forEach { entry ->
                add(buildEntry(entry, outgoingOpacity(entry, progress)))
            }
            val currentIncoming = checkNotNull(incomingEntry)
            add(buildEntry(currentIncoming, incomingOpacity(currentIncoming, progress)))
        }
        return Stack(
            children = children,
            alignment = Alignment.TOP_START,
            key = AnimatedSwitcherStackKey,
        )
    }

    /** Creates one opacity wrapper whose stable key owns exactly one retained child subtree. */
    private fun buildEntry(entry: AnimatedSwitcherEntry, rawOpacity: Float): Widget {
        return Opacity(
            opacity = quantizeOpacity(rawOpacity.coerceIn(0f, 1f)),
            child = entry.child,
            key = AnimatedSwitcherEntryKey(entry.id),
        )
    }

    /** Returns the current normalized curved progress or one while idle. */
    private fun transitionProgress(): Float {
        return if (isTransitioning) curved.value.coerceIn(0f, 1f) else 1f
    }

    /** Resolves one outgoing entry's raw opacity in the current segment. */
    private fun outgoingOpacity(entry: AnimatedSwitcherEntry, progress: Float): Float {
        return entry.startOpacity * (1f - progress)
    }

    /** Resolves the incoming entry's raw opacity in the current segment. */
    private fun incomingOpacity(entry: AnimatedSwitcherEntry, progress: Float): Float {
        return entry.startOpacity + (1f - entry.startOpacity) * progress
    }

    /** Allocates one state-local visual entry without inspecting or rewriting the child key. */
    private fun createEntry(child: Widget, startOpacity: Float): AnimatedSwitcherEntry {
        nextEntryId += 1L
        return AnimatedSwitcherEntry(
            id = nextEntryId,
            child = child,
            startOpacity = startOpacity.coerceIn(0f, 1f),
        )
    }

    /** Mirrors retained Element update compatibility for child transition decisions. */
    private fun canUpdate(oldChild: Widget, newChild: Widget): Boolean {
        return oldChild::class == newChild::class && oldChild.key == newChild.key
    }
}
