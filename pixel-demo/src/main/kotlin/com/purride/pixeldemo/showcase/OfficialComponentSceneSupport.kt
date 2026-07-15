package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.Align
import com.purride.pixelui.AlignDirectional
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.AppScaffold
import com.purride.pixelui.AspectRatio
import com.purride.pixelui.Badge
import com.purride.pixelui.BuildContext
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.Center
import com.purride.pixelui.Checkbox
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Column
import com.purride.pixelui.ConfirmDialog
import com.purride.pixelui.ConstrainedBox
import com.purride.pixelui.Container
import com.purride.pixelui.ContainerDirectional
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.Dialog
import com.purride.pixelui.Directionality
import com.purride.pixelui.Divider
import com.purride.pixelui.Dropdown
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Expanded
import com.purride.pixelui.FittedBox
import com.purride.pixelui.Flexible
import com.purride.pixelui.Gap
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.Icon
import com.purride.pixelui.ImeAvoidingView
import com.purride.pixelui.KeyboardAvoidingView
import com.purride.pixelui.LoadStateView
import com.purride.pixelui.ListTile
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Menu
import com.purride.pixelui.Opacity
import com.purride.pixelui.OptionList
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PaddingDirectional
import com.purride.pixelui.PixelBoxConstraints
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelAsyncSnapshot
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelOverlayBarrier
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayDismissPolicy
import com.purride.pixelui.PixelOverlayDismissReason
import com.purride.pixelui.PixelOverlayEntry
import com.purride.pixelui.PixelOverlayLayer
import com.purride.pixelui.PixelOverlayLifecycle
import com.purride.pixelui.PixelOverlayMotion
import com.purride.pixelui.PixelOverlayOutcome
import com.purride.pixelui.PixelPopoverAlignment
import com.purride.pixelui.PixelPopoverPlacement
import com.purride.pixelui.PixelPopupRoute
import com.purride.pixelui.PixelSnackbarQueueController
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelToastQueueController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.Popover
import com.purride.pixelui.Positioned
import com.purride.pixelui.PositionedDirectional
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.SafeArea
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SelectionList
import com.purride.pixelui.SectionList
import com.purride.pixelui.SectionListSection
import com.purride.pixelui.ShortcutHint
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slider
import com.purride.pixelui.Snackbar
import com.purride.pixelui.SnackbarQueue
import com.purride.pixelui.Spacer
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Stepper
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextButton
import com.purride.pixelui.Toast
import com.purride.pixelui.ToastQueue
import com.purride.pixelui.Tooltip
import com.purride.pixelui.Transform
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.Visibility
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.animation.IntOffset
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Panel
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun officialComponentScene(
    id: String,
    title: String,
    summary: String,
    category: com.purride.pixeldemo.catalog.DemoCategory,
    api: String,
    extraApis: Set<String> = emptySet(),
    body: (DemoEnv) -> Widget,
): DemoScene =
    ComponentExampleScene(
        id = id,
        title = title,
        summary = summary,
        category = category,
        tags = setOf("component", title.lowercase()),
        apis = setOf(api) + extraApis,
        bodyBuilder = { env ->
            officialBody(
                listOf(
                    samplePanel(title = "Example", color = categoryColor(category.id), child = body(env)),
                ),
            )
        },
    )

fun officialBody(children: List<Widget>): Widget =
    Column(
        children = children,
        spacing = 4,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

fun stackStage(child: Widget): Widget =
    Container(width = 86, height = 34, borderColor = Accent, child = Stack(children = listOf(PositionedFill(child = Container(fillColor = PixelColor.fromRgb(12, 12, 12))), child)))

fun exampleBox(label: String, color: PixelColor): Widget =
    Container(
        padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
        borderColor = color,
        fillColor = PixelColor.fromArgb(80, color.red, color.green, color.blue),
        child = Text(label, style = TextStyle(color = color)),
    )

private fun categoryColor(id: String): PixelColor = when (id) {
    DemoCatalog.layout.id -> Accent
    DemoCatalog.text.id -> Cyan
    DemoCatalog.input.id -> Cyan
    DemoCatalog.controls.id -> Green
    DemoCatalog.feedback.id -> Pink
    else -> Accent
}

class TextFieldOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TextFieldOfficialState()

    private class TextFieldOfficialState : State<TextFieldOfficialBody>() {
        private val controller = TextEditingController()
        private val state = controller.create("TextField")
        private var status = "READY"

        override fun build(context: BuildContext): Widget =
            officialBody(
                listOf(
                    samplePanel(
                        title = "Example",
                        color = Cyan,
                        child = Column(
                            children = listOf(
                                TextField(
                                    state = state,
                                    controller = controller,
                                    placeholder = "输入文本",
                                    inputType = PixelInputType.TEXT,
                                    textInputAction = PixelTextInputAction.DONE,
                                    onChanged = { value -> status = "typing ${value.length}"; setState {} },
                                    onSubmitted = { value -> status = "submit ${value.length}"; setState {} },
                                    borderColor = Cyan,
                                    fillColor = PixelColor.fromRgb(8, 16, 20),
                                ),
                                Text(status, style = TextStyle(color = Muted)),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
            )
    }
}

class ButtonOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ButtonOfficialState()

    private class ButtonOfficialState : State<ButtonOfficialBody>() {
        private var count = 0

        override fun build(context: BuildContext): Widget {
            val style: PixelButtonStyle = ButtonStyle(
                fillColor = PixelColor.fromRgb(24, 18, 4),
                borderColor = Yellow,
                textStyle = PixelTextStyle(color = Yellow),
            )
            return officialBody(
                listOf(
                    samplePanel(
                        title = "Example",
                        color = Green,
                        child = Column(
                            children = listOf(
                                Row(
                                    children = listOf(
                                        OutlinedButton(text = "PRESS", onPressed = { count += 1; setState {} }, borderColor = Accent),
                                        OutlinedButton(text = "STYLE", onPressed = {}, style = style),
                                        OutlinedButton(text = "OFF", onPressed = null, enabled = false),
                                    ),
                                    spacing = 2,
                                ),
                                Text("count=$count", style = TextStyle(color = Muted)),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
            )
        }
    }
}

class CheckboxOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CheckboxOfficialState()

    private class CheckboxOfficialState : State<CheckboxOfficialBody>() {
        private var checked = true
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Green, child = Row(children = listOf(Checkbox(checked = checked, onChanged = { value -> checked = value; setState {} }, activeColor = Green), Text(if (checked) "checked" else "unchecked", style = TextStyle(color = Green))), spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER))))
    }
}

class SwitchOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SwitchOfficialState()

    private class SwitchOfficialState : State<SwitchOfficialBody>() {
        private var checked = false
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Blue, child = Row(children = listOf(Switch(checked = checked, onChanged = { value -> checked = value; setState {} }), Text(if (checked) "on" else "off", style = TextStyle(color = Blue))), spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER))))
    }
}

class TabsOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TabsOfficialState()

    private class TabsOfficialState : State<TabsOfficialBody>() {
        private var selected = 1
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Pink, child = Tabs(labels = listOf("A", "B", "C"), selectedIndex = selected, onSelected = { index -> selected = index; setState {} }))))
    }
}

class SegmentedOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SegmentedOfficialState()

    private class SegmentedOfficialState : State<SegmentedOfficialBody>() {
        private var selected = 0
        override fun build(context: BuildContext): Widget =
            officialBody(listOf(samplePanel("Example", color = Pink, child = SegmentedControl(labels = listOf("DAY", "NIGHT", "AUTO"), selectedIndex = selected, onSelected = { index -> selected = index; setState {} }))))
    }
}

class StepperOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StepperOfficialState()

    private class StepperOfficialState : State<StepperOfficialBody>() {
        private var value = 4

        override fun build(context: BuildContext): Widget =
            officialBody(
                listOf(
                    samplePanel(
                        title = "Stepper",
                        color = Accent,
                        child = Stepper(
                            value = value,
                            range = 0..10,
                            step = 2,
                            onChanged = { next -> value = next; setState {} },
                            valueText = "$value PX",
                            valueWidth = 34,
                        ),
                    ),
                    samplePanel(
                        title = "ValueAdjuster",
                        color = Cyan,
                        child = ValueAdjuster(
                            valueText = "FAST",
                            onDecrease = {},
                            onIncrease = {},
                            label = "SPEED",
                            valueWidth = 34,
                        ),
                    ),
                ),
            )
    }
}

/**
 * Interactive Toast/Snackbar FIFO showcase using finite Host active-time dwell periods.
 *
 * @param itemTimeout Finite timeout applied independently after each item reaches the queue head.
 * @param key Optional retained identity supplied by the catalog scene.
 */
class ToastQueueOfficialBody(
    private val itemTimeout: Duration = 6.seconds,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates the queue owners and monotonic demo batch counters. */
    override fun createState(): State<out StatefulWidget> = ToastQueueOfficialState()

    /** Owns both notification lanes for the complete component-scene lifetime. */
    private class ToastQueueOfficialState : State<ToastQueueOfficialBody>() {
        /** Toast FIFO retained independently from the bottom Snackbar lane. */
        private val toastQueue: PixelToastQueueController = PixelToastQueueController()

        /** Snackbar FIFO retaining each action until its item becomes visible. */
        private val snackbarQueue: PixelSnackbarQueueController = PixelSnackbarQueueController()

        /** Monotonic label source proving two Toast items are appended per interaction. */
        private var toastBatch: Int = 0

        /** Monotonic label source proving two Snackbar items are appended per interaction. */
        private var snackbarBatch: Int = 0

        /** Last explicit queue or Snackbar-action event shown beside the controls. */
        private var status: String = "READY — EACH ITEM USES ACTIVE-TIME TIMEOUT"

        /** Builds queue controls and the shared non-modal notification presentation lane. */
        override fun build(context: BuildContext): Widget =
            officialBody(
                listOf(
                    samplePanel(
                        title = "FIFO controls",
                        color = Accent,
                        child = Column(
                            children = listOf(
                                Wrap(
                                    children = listOf(
                                        OutlinedButton(
                                            text = "+2 TOAST",
                                            onPressed = ::enqueueToastPair,
                                            borderColor = Accent,
                                            key = "notification-enqueue-toast-pair",
                                        ),
                                        OutlinedButton(
                                            text = "NEXT TOAST",
                                            onPressed = ::dismissCurrentToast,
                                            borderColor = Cyan,
                                            key = "notification-next-toast",
                                        ),
                                        OutlinedButton(
                                            text = "+2 SNACK",
                                            onPressed = ::enqueueSnackbarPair,
                                            borderColor = Green,
                                            key = "notification-enqueue-snackbar-pair",
                                        ),
                                        OutlinedButton(
                                            text = "NEXT SNACK",
                                            onPressed = ::dismissCurrentSnackbar,
                                            borderColor = Pink,
                                            key = "notification-next-snackbar",
                                        ),
                                    ),
                                    spacing = 2,
                                    runSpacing = 2,
                                ),
                                Text(status, style = TextStyle(color = Muted), key = "notification-status"),
                                Text(
                                    "FIFO: SECOND STARTS ONLY AFTER FIRST • PAUSE FREEZES TIME",
                                    style = TextStyle(color = Muted),
                                ),
                            ),
                            spacing = 2,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    samplePanel(
                        title = "Live notification lane",
                        color = Pink,
                        child = Container(
                            width = 96,
                            height = 42,
                            borderColor = Muted,
                            child = Stack(
                                children = listOf(
                                    Center(child = Text("NON-MODAL CONTENT", style = TextStyle(color = Muted))),
                                    ToastQueue(controller = toastQueue, key = "official-toast-queue"),
                                    Positioned(
                                        left = 0,
                                        right = 0,
                                        bottom = 0,
                                        child = SnackbarQueue(
                                            controller = snackbarQueue,
                                            key = "official-snackbar-queue",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )

        /** Appends two finite-time Toast items in one user action to make FIFO order observable. */
        private fun enqueueToastPair() {
            toastBatch += 1
            /** Human-readable prefix shared by the pair while preserving queue order. */
            val batchLabel = "TOAST $toastBatch"
            toastQueue.enqueue(
                message = "$batchLabel.1",
                timeout = widget.itemTimeout,
                textStyle = TextStyle(color = Accent),
            )
            toastQueue.enqueue(
                message = "$batchLabel.2",
                timeout = widget.itemTimeout,
                textStyle = TextStyle(color = Cyan),
            )
            setState { status = "QUEUED $batchLabel.1 → $batchLabel.2" }
        }

        /** Appends one actionable Snackbar followed by a second finite-time FIFO item. */
        private fun enqueueSnackbarPair() {
            snackbarBatch += 1
            /** Human-readable prefix shared by the pair while preserving queue order. */
            val batchLabel = "SNACK $snackbarBatch"
            snackbarQueue.enqueue(
                message = "$batchLabel.1",
                actionLabel = "ACK",
                onAction = {
                    setState { status = "ACTION ACK: $batchLabel.1" }
                },
                timeout = widget.itemTimeout,
            )
            snackbarQueue.enqueue(
                message = "$batchLabel.2",
                timeout = widget.itemTimeout,
            )
            setState { status = "QUEUED $batchLabel.1 → $batchLabel.2" }
        }

        /** Manually advances the Toast lane without changing the successor's own timeout. */
        private fun dismissCurrentToast() {
            /** Whether a visible or pending Toast was actually removed. */
            val advanced = toastQueue.dismissCurrent()
            setState { status = if (advanced) "TOAST ADVANCED" else "TOAST QUEUE EMPTY" }
        }

        /** Manually advances the Snackbar lane without invoking its optional action. */
        private fun dismissCurrentSnackbar() {
            /** Whether a visible or pending Snackbar was actually removed. */
            val advanced = snackbarQueue.dismissCurrent()
            setState { status = if (advanced) "SNACKBAR ADVANCED" else "SNACKBAR QUEUE EMPTY" }
        }
    }
}

/**
 * Unified production-overlay route showcase with typed completion and reasoned dismissal output.
 *
 * @param key Optional retained identity supplied by the catalog scene.
 */
class ProductionOverlayOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates one retained controller so route identity survives ordinary scene rebuilds. */
    override fun createState(): State<out StatefulWidget> = ProductionOverlayOfficialState()

    /** Owns route configuration, the active typed entry, and visible outcome history. */
    private class ProductionOverlayOfficialState : State<ProductionOverlayOfficialBody>() {
        /** Unified route owner used by the showcase's single PixelOverlayHost. */
        private val overlayController: PixelOverlayController = PixelOverlayController()

        /** Currently configured paint layer for the next route. */
        private var selectedLayer: PixelOverlayLayer = PixelOverlayLayer.Modal

        /** Whether the next route consumes Back/barrier taps instead of dismissing. */
        private var lockedPolicy: Boolean = false

        /** Typed entry retained until its exit presentation has delivered one outcome. */
        private var activeEntry: PixelOverlayEntry<String>? = null

        /** Latest route lifecycle event rendered outside the modal surface. */
        private var outcomeText: String = "RESULT: NONE"

        /** Builds route configuration controls under the unified overlay host. */
        override fun build(context: BuildContext): Widget {
            /** True while a logical route or its retained exit presentation is still owned here. */
            val routeBusy = activeEntry?.lifecycle != null &&
                activeEntry?.lifecycle != PixelOverlayLifecycle.Disposed
            /** Human-readable next-route policy shown without pretending to fire platform Back. */
            val policyLabel = if (lockedPolicy) "CONSUME" else "DISMISS"
            /** Scene content kept under the route so modal isolation is visible during interaction. */
            val content = samplePanel(
                title = "Typed PixelPopupRoute",
                color = Purple,
                child = Container(
                    width = 96,
                    height = 58,
                    borderColor = Muted,
                    padding = EdgeInsets.all(2),
                    child = Column(
                        children = listOf(
                            Wrap(
                                children = listOf(
                                    OutlinedButton(
                                        text = "LAYER ${selectedLayer.name.uppercase()}",
                                        onPressed = if (routeBusy) null else ::toggleLayer,
                                        enabled = !routeBusy,
                                        borderColor = Purple,
                                        key = "overlay-route-layer",
                                    ),
                                    OutlinedButton(
                                        text = "BACK $policyLabel",
                                        onPressed = if (routeBusy) null else ::togglePolicy,
                                        enabled = !routeBusy,
                                        borderColor = Cyan,
                                        key = "overlay-route-policy",
                                    ),
                                    OutlinedButton(
                                        text = "SHOW ROUTE",
                                        onPressed = if (routeBusy) null else ::showTypedRoute,
                                        enabled = !routeBusy,
                                        borderColor = Accent,
                                        key = "overlay-route-show",
                                    ),
                                    OutlinedButton(
                                        text = "CLOSE ACTIVE",
                                        onPressed = if (routeBusy) ::dismissActiveRoute else null,
                                        enabled = routeBusy,
                                        borderColor = Pink,
                                        key = "overlay-route-close",
                                    ),
                                ),
                                spacing = 2,
                                runSpacing = 2,
                            ),
                            Text(
                                "BARRIER=DIM • BACK/BARRIER=$policyLabel • DEVICE BACK IS REAL INPUT",
                                style = TextStyle(color = Muted),
                            ),
                            Text(outcomeText, style = TextStyle(color = Accent), key = "overlay-route-outcome"),
                        ),
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
            )
            return PixelOverlayHost(
                controller = overlayController,
                child = content,
                key = widget.key?.let { "$it-overlay-host" } ?: "production-overlay-host",
            )
        }

        /** Alternates the next route between app-level Modal and topmost System paint layers. */
        private fun toggleLayer() {
            setState {
                selectedLayer = if (selectedLayer == PixelOverlayLayer.Modal) {
                    PixelOverlayLayer.System
                } else {
                    PixelOverlayLayer.Modal
                }
            }
        }

        /** Alternates between dismissible and locked Back/barrier policies for the next route. */
        private fun togglePolicy() {
            setState { lockedPolicy = !lockedPolicy }
        }

        /** Presents one typed modal route whose action, Back, and barrier outcomes stay visible. */
        private fun showTypedRoute() {
            if (activeEntry?.lifecycle != null && activeEntry?.lifecycle != PixelOverlayLifecycle.Disposed) return
            /** Immutable layer snapshot displayed inside the route that owns it. */
            val routeLayer = selectedLayer
            /** Immutable dismissal policy snapshot displayed inside the route that owns it. */
            val routePolicy = if (lockedPolicy) {
                PixelOverlayDismissPolicy.Locked
            } else {
                PixelOverlayDismissPolicy.Dismissible
            }
            /** Typed entry assigned immediately after show and captured by its own action buttons. */
            lateinit var routeEntry: PixelOverlayEntry<String>
            routeEntry = overlayController.show(
                PixelPopupRoute(
                    content = Dialog(
                        title = Text("TYPED ROUTE", style = TextStyle(color = Accent)),
                        content = Column(
                            children = listOf(
                                Text("LAYER=${routeLayer.name.uppercase()}", style = TextStyle(color = Purple)),
                                Text(
                                    if (lockedPolicy) {
                                        "BACK/BARRIER CONSUME; USE AN ACTION"
                                    } else {
                                        "BACK/BARRIER DISMISS WITH A REASON"
                                    },
                                    style = TextStyle(color = Muted),
                                ),
                            ),
                            spacing = 2,
                        ),
                        actions = listOf(
                            TextButton(
                                text = "RETURN YES",
                                onPressed = { routeEntry.complete("YES") },
                                key = "overlay-route-complete",
                            ),
                            TextButton(
                                text = "DISMISS",
                                onPressed = {
                                    routeEntry.dismiss(PixelOverlayDismissReason.Programmatic)
                                },
                                key = "overlay-route-dismiss",
                            ),
                        ),
                        modal = false,
                    ),
                    layer = routeLayer,
                    dismissPolicy = routePolicy,
                    barrier = PixelOverlayBarrier(color = PixelColor.fromArgb(150, 0, 0, 0)),
                    modal = true,
                    motion = PixelOverlayMotion.Dialog,
                    onOutcome = { outcome ->
                        activeEntry = null
                        setState { outcomeText = describeOutcome(outcome) }
                    },
                ),
            )
            activeEntry = routeEntry
            setState {
                outcomeText = "ACTIVE: ${routeLayer.name.uppercase()} / ${if (lockedPolicy) "LOCKED" else "DISMISSIBLE"}"
            }
        }

        /** Closes the current route through the explicit reasoned entry API. */
        private fun dismissActiveRoute() {
            activeEntry?.dismiss(PixelOverlayDismissReason.Programmatic)
        }

        /** Formats typed completion or stable dismissal reason for persistent visual evidence. */
        private fun describeOutcome(outcome: PixelOverlayOutcome<String>): String =
            when (outcome) {
                is PixelOverlayOutcome.Completed -> "RESULT: COMPLETED(${outcome.result})"
                is PixelOverlayOutcome.Dismissed -> "RESULT: DISMISSED(${outcome.reason.name.uppercase()})"
            }
    }
}

/** Controlled overlay kind selected by the official Popover/Menu/Dropdown/Tooltip scene. */
private enum class OfficialOverlayControl {
    /** Collision-aware anchored Popover. */
    Popover,

    /** Menu hosted inside a single owning Popover. */
    Menu,

    /** Controlled Dropdown and its nested Menu. */
    Dropdown,

    /** Non-modal informational Tooltip. */
    Tooltip,
}

/**
 * Controlled anchored-overlay showcase that permits at most one expanded presentation at a time.
 *
 * @param key Optional retained identity supplied by the catalog scene.
 */
class OverlayControlsOfficialBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates the single-selection overlay controller state. */
    override fun createState(): State<out StatefulWidget> = OverlayControlsOfficialState()

    /** Owns the one-active-overlay invariant and structured menu selection. */
    private class OverlayControlsOfficialState : State<OverlayControlsOfficialBody>() {
        /** Sole overlay allowed to be logically expanded; null closes every presentation. */
        private var activeOverlay: OfficialOverlayControl? = null

        /** Current structured value rendered by the controlled Dropdown. */
        private var selected: String = "A"

        /** Last menu command or explicit close event rendered beside the selector. */
        private var status: String = "ACTIVE: NONE — OPEN ONE CONTROL"

        /** Builds four controlled anchors backed by one mutually exclusive visibility value. */
        override fun build(context: BuildContext): Widget =
            officialBody(
                listOf(
                    samplePanel(
                        title = "One overlay at a time",
                        color = Purple,
                        child = Column(
                            children = listOf(
                                Text(status, style = TextStyle(color = Muted), key = "overlay-control-status"),
                                OutlinedButton(
                                    text = "CLOSE ACTIVE",
                                    onPressed = if (activeOverlay == null) null else ::closeOverlay,
                                    enabled = activeOverlay != null,
                                    borderColor = Purple,
                                    key = "overlay-control-close",
                                ),
                            ),
                            spacing = 2,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    samplePanel(
                        title = "Popover — AUTO flip / CENTER",
                        color = Accent,
                        child = Popover(
                            anchor = OutlinedButton(
                                text = "OPEN POPOVER",
                                onPressed = { toggleOverlay(OfficialOverlayControl.Popover) },
                                borderColor = Accent,
                                key = "overlay-open-popover",
                            ),
                            content = Container(
                                padding = EdgeInsets.all(2),
                                fillColor = PixelColor.Black,
                                borderColor = Accent,
                                child = Column(
                                    children = listOf(
                                        Text("REAL ANCHOR BOUNDS", style = TextStyle(color = Accent)),
                                        TextButton(
                                            text = "CLOSE",
                                            onPressed = ::closeOverlay,
                                            key = "overlay-popover-close",
                                        ),
                                    ),
                                    spacing = 2,
                                ),
                            ),
                            expanded = activeOverlay == OfficialOverlayControl.Popover,
                            dismissible = true,
                            onDismiss = ::closeOverlay,
                            contentOffset = IntOffset(0, 12),
                            placement = PixelPopoverPlacement.Auto,
                            alignment = PixelPopoverAlignment.Center,
                            key = "official-popover",
                        ),
                    ),
                    samplePanel(
                        title = "Menu — one modal owner",
                        color = Cyan,
                        child = Popover(
                            anchor = OutlinedButton(
                                text = "OPEN MENU",
                                onPressed = { toggleOverlay(OfficialOverlayControl.Menu) },
                                borderColor = Cyan,
                                key = "overlay-open-menu",
                            ),
                            content = Menu(
                                items = listOf(
                                    PixelMenuItem(
                                        label = "COPY",
                                        shortcut = "A",
                                        onSelected = { selectMenuCommand("COPY") },
                                        key = "overlay-menu-copy",
                                    ),
                                    PixelMenuItem(
                                        label = "PASTE",
                                        shortcut = "B",
                                        onSelected = { selectMenuCommand("PASTE") },
                                        key = "overlay-menu-paste",
                                    ),
                                    PixelMenuItem(
                                        label = "CLOSE",
                                        onSelected = ::closeOverlay,
                                        key = "overlay-menu-close",
                                    ),
                                ),
                                onDismissRequest = ::closeOverlay,
                                modal = false,
                                key = "official-menu",
                            ),
                            expanded = activeOverlay == OfficialOverlayControl.Menu,
                            dismissible = true,
                            onDismiss = ::closeOverlay,
                            contentOffset = IntOffset(0, 12),
                            placement = PixelPopoverPlacement.Auto,
                            alignment = PixelPopoverAlignment.Start,
                            key = "official-menu-popover",
                        ),
                    ),
                    samplePanel(
                        title = "Dropdown — controlled selection",
                        color = Green,
                        child = Dropdown(
                            label = "MODE",
                            selectedText = selected,
                            expanded = activeOverlay == OfficialOverlayControl.Dropdown,
                            onToggle = { toggleOverlay(OfficialOverlayControl.Dropdown) },
                            items = listOf(
                                PixelMenuItem(
                                    label = "A",
                                    selected = selected == "A",
                                    onSelected = { selectDropdownValue("A") },
                                    key = "overlay-dropdown-a",
                                ),
                                PixelMenuItem(
                                    label = "B",
                                    selected = selected == "B",
                                    onSelected = { selectDropdownValue("B") },
                                    key = "overlay-dropdown-b",
                                ),
                                PixelMenuItem(
                                    label = "CLOSE",
                                    onSelected = ::closeOverlay,
                                    key = "overlay-dropdown-close",
                                ),
                            ),
                            key = "official-dropdown",
                        ),
                    ),
                    samplePanel(
                        title = "Tooltip — non-modal",
                        color = Pink,
                        child = Tooltip(
                            message = "RESIZES WITH THE SAFE VIEWPORT",
                            visible = activeOverlay == OfficialOverlayControl.Tooltip,
                            child = OutlinedButton(
                                text = if (activeOverlay == OfficialOverlayControl.Tooltip) "HIDE TIP" else "SHOW TIP",
                                onPressed = { toggleOverlay(OfficialOverlayControl.Tooltip) },
                                borderColor = Pink,
                                key = "overlay-tooltip-anchor",
                            ),
                            key = "official-tooltip",
                        ),
                    ),
                ),
            )

        /** Toggles [target] while closing any previously selected overlay in the same rebuild. */
        private fun toggleOverlay(target: OfficialOverlayControl) {
            setState {
                activeOverlay = if (activeOverlay == target) null else target
                status = activeOverlay?.let { active -> "ACTIVE: ${active.name.uppercase()}" }
                    ?: "ACTIVE: NONE"
            }
        }

        /** Closes the sole active overlay through its explicit controlled callback. */
        private fun closeOverlay() {
            setState {
                activeOverlay = null
                status = "ACTIVE: NONE — CLOSED"
            }
        }

        /** Records a menu command and closes its owning Popover in the same state transition. */
        private fun selectMenuCommand(command: String) {
            setState {
                activeOverlay = null
                status = "MENU: $command"
            }
        }

        /** Applies a structured Dropdown [value] and closes its controlled presentation. */
        private fun selectDropdownValue(value: String) {
            setState {
                selected = value
                activeOverlay = null
                status = "DROPDOWN: $value"
            }
        }
    }
}

class SliderOfficialBody(
    private val showProgress: Boolean,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SliderOfficialState()

    private class SliderOfficialState : State<SliderOfficialBody>() {
        private var value = 0.62f
        override fun build(context: BuildContext): Widget =
            officialBody(
                listOf(
                    samplePanel(
                        title = "Example",
                        color = Accent,
                        child = Column(
                            children = buildList {
                                if (!widget.showProgress) {
                                    add(Slider(value = value, onDrag = { next -> value = next; setState {} }, onRelease = { next -> value = next; setState {} }, activeColor = Accent))
                                }
                                add(ProgressBar(progress = value, width = 72, color = Accent))
                                add(Text("${(value * 100).toInt()}%", style = TextStyle(color = Accent)))
                            },
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
            )
    }
}

fun officialTinyIcon(): PixelBitmap {
    val clear = PixelColor.Transparent.argb
    val c = Accent.argb
    val pixels = intArrayOf(
        clear, clear, c, c, c, clear, clear,
        clear, c, clear, clear, clear, c, clear,
        c, clear, c, clear, c, clear, c,
        c, clear, clear, c, clear, clear, c,
        c, clear, c, clear, c, clear, c,
        clear, c, clear, clear, clear, c, clear,
        clear, clear, c, c, c, clear, clear,
    )
    return PixelBitmap(width = 7, height = 7, pixels = pixels)
}
