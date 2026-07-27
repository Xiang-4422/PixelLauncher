# Changelog

## 1.0.0 - Unreleased

First public stable release of the Android-first Pixel Engine SDK.

### Release highlights

- One consumer-facing artifact, `pixel-engine`, containing the complete SDK surface. Source
  packages retain clear responsibilities without exposing internal module assembly to consumers.
- Stable retained rendering, typed navigation and restoration, lifecycle-safe Host integration,
  accessibility semantics, keyboard/gamepad input, Unicode-safe text editing, adaptive layout,
  theming, animation, resource loading, testing, and debug SPI contracts.
- Reviewed source, Metalava, and JVM ABI baselines; isolated Kotlin/Java/R8 consumers; Apache-2.0
  publication metadata; signed staging, SBOM, provenance, dependency locking, and checksums.
- Navigation exposes exactly one public entry point: every navigation declaration now lives in
  `com.purride.pixelui`, and `PixelNavigator` / `PixelNestedNavigator` / `PixelTypedNavigatorStack`
  accept only typed `PixelRouteRequest` roots. The pre-release string-route layer (`PixelRoute`,
  `currentRoute`, `stack`, `PixelNavigatorSnapshot`, `PixelDeepLinkResolver`, the
  `widgets.navigation` package and its root-package typealiases) is removed.

### Compatibility

- Android `minSdk` 24 and `minCompileSdk` 36.
- Minimum supported toolchain: AGP 8.10.1, Gradle 8.11.1, Kotlin 2.2.10, and Java 11.
- Consumers compiled against the internal `0.1.0-SNAPSHOT` baseline must review the linked
  migration guides and rebuild; `1.0.0` is the first SemVer-stable public line.

### Known limits

- The SDK is Android-first. Compose wrappers are intentionally excluded from the core artifact;
  Compose applications may host the Android View surface from application code.
- Representative device performance baselines and performance soak remain a separate follow-up
  workstream and are not part of this release candidate's acceptance scope.

## 0.1.0-SNAPSHOT

Internal 0.x SDK baseline for `pixel-engine`.

### SDK status

- `pixel-engine` is published as `com.purride:pixel-engine:0.1.0-SNAPSHOT`.
- Public and binary API baselines are tracked under `pixel-engine/api/`.
- The release gate is `tools/pixel-release-check.sh`.

### Breaking changes recorded in this baseline

- `com.purride.pixelui.advanced.Pixel*` RenderObject aliases were replaced by real public SPI
  classes. Source using the `advanced` names remains compatible after recompilation, but binaries
  compiled against the old aliases must be rebuilt because their descriptors referenced
  `com.purride.pixelui.internal.*`. See
  See `pixel-engine/docs/guides/migration.md`.
- `PixelHostView.backgroundColor` was renamed to `bezelColor`.
- `PixelHostView.pixelGridColor` was renamed to `offPixelColor`.
- `PixelPagerController.startDrag` now requires `viewportSizePx`.
- Semantics v2 appends defaulted parameters to the existing `Semantics`, `PixelSemanticsNode`,
  `ListTile`, `PixelMenuItem`, `Menu`, `Dialog`, `TextField`, and `Slider` Kotlin APIs. Existing
  source calls remain valid after recompilation, but binaries compiled against the earlier
  `0.1.0-SNAPSHOT` JVM descriptors must be rebuilt. See
  See `pixel-engine/docs/guides/migration.md`.
- Per-Host focus/input appends defaulted parameters to `Popover`, `Menu`, `Dialog`, `Slider`,
  `Tabs`, `SegmentedControl`, `RefreshIndicator`, and `SwipeRefreshScaffold`. Existing source calls
  remain valid after recompilation, but binaries compiled against the earlier snapshot JVM
  descriptors must be rebuilt. See `pixel-engine/docs/guides/migration.md`.

### Added SDK surface

- Immutable `PixelEngine.Builder` instances with injectable clock, frame scheduler, per-Host ticker
  factory, structured error reporter/logger, resource resolver/cache, Host environment, focused
  Host capabilities, and explicit theme override. New typed Host actions replace string protocols;
  missing and failed optional capabilities return explicit sealed results. Legacy
  `PixelHostBridge`, setup, and frame-scope descriptors remain compatible. See
  `pixel-engine/docs/guides/migration.md`.
- The SDK publication was consolidated into one `pixel-engine` AAR, POM, API/ABI baseline,
  artifact budget, dependency lock and isolated Maven/R8 consumer. Obsolete split-artifact,
  Compose sample, demo and benchmark modules were removed from the main build.
- Thread-safe byte-bounded `PixelResourceCache` with per-kind/global LRU budgets, glyph-pack
  caching, single-flight misses, lock-free loading, observable eviction snapshots, and stale
  in-flight writeback prevention; plus `PixelResourceLoader` for caller-owned executors,
  main-thread rejection, async deduplication, subscriber-only cancellation, prefetch, and bounded
  failure caching. Bitmap/sprite/manifest/glyph inputs now have strict limits, checksum and
  corruption validation, while public pixel/glyph arrays use defensive copies. Existing resource
  constructors and `copy$default` JVM descriptors remain present. See
  `pixel-engine/docs/guides/migration.md`.
- `PixelHostFrameDiagnostics`, exclusive `PixelFrameTimings`, `PixelFrameWorkload`, and
  `PixelFrameDropReason` for opt-in build/layout/paint/buffer-submit/Android-draw timing, dirty and
  pixel work, ART allocation/GC deltas, cache activity, refresh-rate budgets, and deadline
  attribution. Disabled Hosts do not sample ART or allocate diagnostics snapshots. See
  `pixel-engine/docs/guides/migration.md`.
- A real advanced RenderObject SPI, `@PixelExperimentalApi`, isolated Maven consumer tests,
  Metalava compatibility gates, and an old-consumer-binary runtime fixture.
- Entry-backed typed navigation with `PixelRouteDestination<A, R>`, `PixelRouteRequest<A, R>`,
  independent `PixelRouteEntry<A, R>` instances, explicit `PixelRouteOutcome`, entry-local typed
  state, lifecycle inspection, navigation observers, and structured failures. See
  `pixel-engine/docs/guides/migration.md`.
- Versioned typed Navigator snapshots, destination-owned argument/state migration, typed roots,
  nested and retained multi-stack navigation, validated typed deep links, and Android predictive
  back progress. See `pixel-engine/docs/guides/migration.md`.
- `PixelErrorBoundary` and `PixelErrorPanel` for build/render-time widget fallback.
- `PixelBackDispatcher`, discrete/predictive back handlers, automatic API 33/34+ Host registration,
  and the API 24–32 `PixelHostView.handleBackPressed` compatibility path.
- Orthogonal Host attachment/owner lifecycle, automatic ViewTree owner binding, explicit
  start/resume/pause/stop/destroy APIs, lifecycle diagnostics, and Host-owned cancellable
  frame/ticker scopes. See `pixel-engine/docs/guides/migration.md`.
- `PixelHapticFeedback` for widget-level host haptic requests.
- `PixelHostView.dispose` and `PixelHostSetup.dispose` as idempotent destroy-compatible teardown.
- Typed `PixelPopupRoute<R>`, `PixelOverlayEntry<R>`, explicit overlay layers, dismiss policies,
  barriers, lifecycle/outcome reasons, `PixelOverlaySurface`, and compatibility handles on
  `PixelOverlayController`/`PixelOverlayHost`.
- `PixelTester`, `PixelFinder`, and `PixelTestGesture` as SDK testing APIs.
- Stable retained semantics trees, complete state/range/collection/live-region properties, typed
  accessibility actions, per-Host monotonic Android virtual IDs, focus/hover/event bridging, and
  structured `PixelTester` semantics assertions. See
  `pixel-engine/docs/guides/migration.md`.
- `PixelThemeTokens` with semantic light/dark/high-contrast color schemes, typography, spacing,
  sizes, stair-step radii, integer borders, hard-shadow elevation, motion, labels, and 21 component
  token families; legacy `PixelThemeData` remains available through a compatibility projection.
- `PixelControlStateSet`, `PixelStateProperty`, and `PixelStateMap` with one shared eight-state
  priority contract, additive focus indicators, Loading focus retention, and Disabled traversal
  removal. See `pixel-engine/docs/guides/migration.md`.
- `Visibility` for simple child/replacement switching.
- `LoadStateView` for `PixelAsyncSnapshot` loading/empty/error/content rendering.
- `SelectionList` and `OptionList` for controlled single-choice lists.
- `SectionList` and `SectionListSection` for grouped list layout.
- `ValueAdjuster` and `Stepper` for controlled pixel value editing.
- `ImeAvoidingView` and `KeyboardAvoidingView` for `MediaQuery.viewInsets` padding.
- `ModalBarrier`, `ToastQueue`, `PixelToastQueueController`, and `PixelToastQueueItem` for overlay feedback foundations.
- `SwipeRefreshScaffold` for refreshable screen layout with optional bars.
- `AnimatedVisibility` for animated child/replacement visibility changes.
- `PixelMotionTheme`, role-aware motion tokens, spring settle parameters, Host-injected live Android
  animator settings, and `PixelHostView.motionSettingsOverride`. See
  `pixel-engine/docs/guides/migration.md`.
- `FocusTraversalGroup` for local focus traversal policy scopes.
- `ShortcutHint` for visual keyboard/gamepad shortcut hints.
- Root-portal `Popover`, `Menu`, `PixelMenuItem`, `Dropdown`, and `Tooltip` with measured anchors,
  edge flip/collision handling, RTL alignment, safe insets, and resize relayout.
- Safe-area/IME-aware `Dialog` and `BottomSheet`, plus independent FIFO `ToastQueue` and
  `SnackbarQueue` controllers with active-time timeout behavior and live-region semantics.
- `TextButton` and `PixelTextButtonStyle`.
- `EmptyState` and `ConfirmDialog` feedback components.
- `Slidable`, `SlidableAction`, and `SlidableActionPane`.
- `PixelHostBridge.updateTextInput`.
- `PixelListController.isAtEnd`.
- `PixelPagerSavedState` / `PixelTextFieldSavedState` Android `Bundle` helpers.
- Android accessibility bridge backed by the existing semantics tree.
- Runtime-local Host focus ownership, `PixelKey.SPACE`, and
  `PixelHostView.dispatchPixelKeyEvent` for custom keyboard/gamepad bridges.
- Fixed Unicode 17.0.0 `PixelGraphemeBoundaryMap` / `PixelUtf16Range`, grapheme-safe TextField
  Controller operations, exact `PixelTextInputEvent`, `PixelTester.pressText`, and the additive
  `PixelTextEditingHostBridge` / `PixelTextEditingValue` composition contract. See
  `pixel-engine/docs/guides/migration.md`.
- Additive code-point `Int` glyph source/provider overloads, full-scalar glyph-pack/cache lookup,
  `PixelClusterTextRasterizer`, grapheme-cluster paragraph units, and fixed Unicode 17.0.0 UAX #9
  revision 51 visual ordering. See
  `pixel-engine/docs/guides/migration.md`.
- Orthogonal `PixelViewportPolicy`, explicit `PixelHostProfilePolicy`, automatic Android Host
  capability observation, raw physical system/IME/cutout reprojection, capability-aware text scale
  and contrast presets, RTL Flex visual ordering, and `PixelAdaptiveLayoutData`/`AdaptiveBuilder`.
  See `pixel-engine/docs/guides/migration.md`.

### Adaptive Host behavior changes

- Frozen `ScaleMode.FIT_CENTER` remains Contain + Integer + Center. Explicit policies can combine
  Contain/Cover, Integer/Fractional and nine alignments without changing legacy descriptors.
- Direct `screenProfile` assignment selects `PixelHostProfilePolicy.Fixed`; adaptive px/dp/logical
  policies re-resolve after size, density or viewport-strategy changes.
- Android API 24–29 now separates a larger current IME edge from stable system bars instead of
  unconditionally publishing zero `viewInsets`; API 30+ uses typed system-bar/IME/cutout channels.
- Physical platform insets and cutout rectangles are retained and reprojected after geometry
  changes, so paint, touch, Accessibility and logical inset data share one viewport transform.
- Text, RichText and TextField consume Host text scale. RTL Row reverses visual placement while
  preserving retained declaration and semantics order. `PixelThemeTokens.forHost` selects a
  standard or high-contrast preset from the inherited capability snapshot.
- The Demo catalog now includes a real `Adaptive & Localization` scene with scoped Host overrides,
  Unicode/IME input and controller-bound multi-stack navigation. Reviewed read-only semantics and
  ASCII pixel goldens cover English/Chinese LTR, RTL, 1x/2x text, high contrast, compact hinge and
  small-window IME cases; tests only write disposable candidates under `build/reports`.

### Unicode text editing behavior changes

- Public selection, composition, and Accessibility offsets remain UTF-16, while caret movement,
  deletion, selected ranges, clipboard edits, gestures, Tester actions, and Android InputConnection
  commands now normalize to Unicode 17 extended-grapheme boundaries.
- Exact text is never NFC-normalized. Decomposed Latin, supplementary emoji, modifiers, ZWJ
  sequences, variation selectors, keycaps, RI flags, and CRLF are edited atomically.
- The default `PixelTextInputBridge` now owns a guarded hidden editor. Stale/closed connections,
  failed or nested batches, target switching, Shift selection anchors, composition-aware surrounding
  deletion, reversed ranges, and API 33/34 attributed operations have explicit production tests on
  API 24 and API 37.
- Explicit custom `EditText` injection remains source/binary compatible but is documented as a weak
  compatibility path without full selection-only/composition-only or InputConnection guarantees.
- `PixelKeyEvent.character` remains limited to one non-surrogate BMP character; supplementary and
  multi-code-point commits use the additive String event path and are never split into surrogate
  events.

### Unicode paragraph and Bidi behavior changes

- `Text`, `RichText`, and `TextField` now share extended-grapheme layout units for wrap, ellipsis,
  letter spacing, caret, pointer hit testing, selection/composition rectangles, handles, and
  Accessibility character locations.
- `GlyphSource` and `GlyphProvider` retain their frozen `Char` entry points while the engine and
  glyph packs use complete scalar `Int` keys. Old providers receive one U+FFFD request for an
  unsupported supplementary scalar instead of two surrogate requests.
- Multi-code-point clusters are passed through only to a `PixelClusterTextRasterizer` that claims
  exact atomic support. Other rasterizers receive one deterministic U+FFFD fallback; clusters made
  only of default-ignorables remain zero-width and unpainted.
- Paragraph visual order and paired punctuation mirroring use engine-owned Unicode 17 UAX #9 data,
  not the platform ICU/JDK version. The backing String and public UTF-16 offsets remain unchanged.
- API 26+ virtual text nodes expose per-UTF-16-unit screen-coordinate character locations from the
  same cluster/Bidi geometry. Every code unit inside one grapheme shares a rectangle and truncated
  source positions return `null`.
- 1.0 does not claim built-in Arabic/Indic contextual shaping, color emoji, every ZWJ ligature, or
  script-specific hyphenation; consumer rasterizers must provide those glyph/shaping capabilities.

### Navigation behavior changes

- Repeated pushes of the same destination or legacy `PixelRoute` now allocate isolated entry IDs,
  state buckets, result channels, retained subtrees, and lifecycle state.
- `maintainState=true` retains the hidden Stateful subtree without background layout, painting,
  hit testing, or finder exposure; `maintainState=false` unmounts it after transition settlement
  and clears its bucket.
- Typed success, including `Success(null)`, is distinct from cancellation. Entries are disposed
  before callbacks are delivered, `clear` preserves bottom-to-top disposal and delivery order, and
  Navigator host disposal terminates all pending entries exactly once.
- Legacy nullable-result APIs remain available: cancellation maps to `null`, and legacy replace
  transfers its pending stack-slot callback. Typed replace instead cancels the old channel and
  creates an independent replacement channel.
- Runtime observer, failure, and inspection snapshots remain diagnostic only. Typed persistence
  uses the checksummed `PixelNavigatorSnapshotCodec`; the existing route-name Bundle format remains
  a separate legacy compatibility path.
- Unknown destinations, unsupported schemas, corrupt bytes, rejected argument/state migration, or
  mismatched multi-stack definitions fail atomically and keep the mounted safe root/stack.
- Typed deep links validate missing/invalid arguments before mutation and report observer events as
  `DeepLink`; multi-stack links select their target only after successful navigation.
- Predictive back start/progress/cancel is non-mutating. Commit produces `Cancelled(Back)` for typed
  entries, while legacy `pop()` retains its historical `Success(null)` behavior.

### Host lifecycle behavior changes

- Window detach no longer destroys the retained/render tree. It makes the Host non-interactive and
  pauses frame, animation, input, gesture, and back work until a valid reattach/resume.
- `PixelHostView` automatically follows `ViewTreeLifecycleOwner`; explicit owner binding and
  start/resume/pause/stop/destroy remain available for custom hosts.
- Pause freezes ticker active time and removes the pending upstream frame. Resume reanchors source
  time and continues without adding the paused wall-clock interval.
- Each Host owns an isolated `PixelHostFrameScope` and `tickerProvider`. Replacing `frameScheduler`
  disposes the old scope/provider and does not migrate running animations.
- `PixelHostView.dispose()` now explicitly means the same irreversible terminal transition as
  `destroy()`; `PixelHostSetup.dispose()` also hides its default input bridge.
- The original `PixelFrameScheduler.scheduleFrame(...): Unit`, `PixelTickerProvider` constructor,
  and `PixelTicker` JVM constructor remain intact; cancellable scheduling and diagnostics are
  additive APIs.

### Animation controller behavior changes

- `PixelAnimationController.forward(from)` and `reverse(from)` now apply explicit starts
  immediately, scale segment time by remaining distance, and retarget from the current visual value
  without a first-frame jump or stale elapsed time.
- `stop()` now cancels ticker scheduling while retaining value/direction status; `isAnimating`
  reflects actual ticker activity. Host pause/resume continues on pause-excluding active time.
- Zero duration settles synchronously, negative duration and `NaN` inputs fail before mutation,
  oversized frame deltas clamp to exact terminal value/status, and disposed controllers reject new
  mutating commands instead of silently dropping them.
- `AnimatedOpacity` now keeps one retained opacity wrapper, interpolates continuously from the
  currently rendered alpha, and suppresses paint, hit testing, interaction targets, and semantics
  exactly at zero opacity while preserving layout and child State.
- `AnimatedSwitcher` now mounts outgoing and incoming keyed subtrees concurrently in a stable
  `Stack`; rapid replacement and reversal preserve every still-visible subtree and dispose it only
  after its visual contribution reaches zero.

### Motion theme and component behavior changes

- Button, Switch, Slider, and Tabs now animate pressed/hover/focus/selection from inherited motion
  tokens, including delay and `None`, without changing layout or delaying controlled logical state.
- Slidable drag remains pointer-direct; release uses the resolved settle curve or spring, its own
  laid-out width, and only exposes click targets inside the visible action strip. A dismissible pane
  without `onDismissed` settles open instead of leaving the row off-screen.
- Dialog and Popover retain outgoing State for visual exit while becoming paint-only immediately.
  Running enter/exit transitions retarget from the current alpha when theme, duration scale, or
  reduce-motion settings change.
- Navigator transition IDs own independent timelines, same-ID policy changes rebase from the
  current visual progress, and custom transition builders present a single retained route subtree
  instead of mounting duplicate page State or semantics.
- MultiStack predictive root return is driven directly by platform progress and commits without a
  second time-based replay. An invalidated child predictive session cannot fall through to an
  unrelated initial-stack switch.
- Host and `PixelTester` bind tap, pressed, slider, and hover ownership to the pointer-down render
  source. Removed, disabled, opacity-zero, and paint-only targets are cancelled exactly once and can
  neither receive a stale release nor retarget the up event to overlapping background UI.
- `Tween.begin` and `Tween.end` are now mutable additive properties so implicit-animation builders
  can rebase a replacement target to the last rendered value. Existing getters and constructor
  descriptors remain binary-compatible; two setters are added.

### Focus, input, and modal behavior changes

- Every `PixelHostView` and `PixelTester` owns an independent focus tree. Key, shortcut, modal,
  text-input, and IME `NEXT` dispatch no longer select a process-global active Host.
- Standard controls create independent retained focus nodes, skip disabled controls, and share one
  business action across pointer, Enter/Space, DPAD/gamepad, and semantics/Switch Access routes.
  Application `Focus.onKeyEvent` handlers bubble from the focused node to ancestors before component
  defaults; Tab/Shift+Tab and directional traversal run last.
- Slider, Tabs, SegmentedControl, ValueAdjuster/Stepper, RefreshIndicator, and Slidable expose
  component-appropriate directional or activation behavior; hidden/inert action panes and disabled
  compound controls do not remain traversal stops.
- Dialog, Menu, and Popover establish modal focus and render-target boundaries by default. They
  select explicit autofocus before the first enabled descendant, trap traversal, consume topmost
  Escape/Back, isolate background interaction/semantics, and restore the opener at logical close.
  Retained exit subtrees stay paint-only and cannot reacquire focus or IME.
- A Menu that directly represents a Popover presentation reuses that Popover token; a Menu opened
  inside another modal, such as Dialog, remains a distinct top layer and restores its inner opener.

### Production overlay behavior changes

- Overlay paint, Back/Escape policy, focus ownership, pointer targets, semantics, and typed results
  use one canonical layer/insertion order. Higher non-modal System routes remain interactive above
  a lower modal, may own focus without becoming a trap, and restore the modal on close; passive
  routes are skipped instead of changing Back/Escape behavior. Standalone modal components own one
  platform Back entry, while route/coalesced presentations avoid duplicate handlers.
- Logical close immediately removes interaction and focus. Hosted outcomes are delivered exactly
  once only after every mounted presentation is disposed, and callback delivery preserves logical
  close FIFO even when exit-animation durations differ.
- Anchored presentations escape ancestor clipping through the root portal while retaining ancestor
  opacity, visual-only target suppression, depth-first nested order, and route-relative z-order.
  Higher siblings captured below opacity/clip/scale/translation scratch contexts keep those effects
  and one paint/target/semantics/raw-hit plane order. Placement recomputes from measured anchor
  geometry when scrolling, resizing, changing insets, or switching text direction.
- Dialog and BottomSheet constrain their surfaces to the merged SafeArea/IME viewport. Oversized
  bodies are clipped to the elastic middle region so visible footer actions remain usable in small
  windows.
- Toast and Snackbar queues display one item per controller in FIFO order. Timeouts count active
  ticker time, freeze while the Host is paused, honor reduce-motion policy, and leave no ticker or
  scheduler callback after completion or disposal.

See [the consolidated migration guide](guides/migration.md).

Migration details and the fixed zero/negative-duration, cancellation, retarget, and key semantics
are documented in the
[consolidated migration guide](guides/migration.md).

### Compatibility policy

`0.x` source and binary compatibility is best-effort. Any intentional breaking change must be
listed here and must update the public/binary API baselines in the same change.
