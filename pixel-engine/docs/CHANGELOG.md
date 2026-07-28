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
- Theming exposes exactly one model and one query pair: `PixelTheme(tokens = ..., child = ...)`
  provides `PixelThemeTokens`, and `PixelTheme.of` / `PixelTheme.maybeOf` return it. The
  pre-release dual model (`PixelThemeData`, `PixelThemeColors`, `PixelThemeTokens.fromLegacy`,
  `PixelThemeTokens.toLegacyThemeData`, `PixelColorScheme.fromLegacy`,
  `PixelColorScheme.toLegacyColors`, `PixelTypographyToken.fromLegacy`) and the
  `tokensOf` / `maybeTokensOf` second query pair are removed.
- Host capabilities expose exactly one model: assemble a `PixelHostCapabilitySet` and inject it
  through `PixelEngine.Builder.hostServices(...)`. `PixelHostView` reads only
  `engine.services.hostServices`, so there is no second Host-level entry point. The pre-release
  aggregate bridge layer (`PixelHostBridge`, `PixelTextEditingHostBridge`, `PixelSystemAction`,
  `PixelHostCapabilitySet.fromLegacyBridge`, `PixelHostView.hostBridge` and the internal
  `PixelHostBridgeScope`) is removed, together with the unused `requestFrame` bridge method —
  frame scheduling stays with `PixelFrameScheduler`. `PixelImeCapability` now takes one
  `PixelTextEditingSession` (`id` + `request` + `value`) on every method, and
  `PixelTextInputBridge` owns a fixed engine-provided hidden editor instead of accepting an
  arbitrary `EditText` with a weak `TextWatcher` write-back path.
- The viewport has exactly one representation: `PixelViewportPolicy`. `ScaleMode`,
  `ScreenProfile.scaleMode`, `PixelViewportPolicy.LegacyFitCenter`, `fromLegacyScaleMode` and the
  policy-less `PixelGridGeometryResolver.resolve` / `mapSurfaceToLogical` overloads are removed.
  `PixelHostView.viewportPolicy` is non-null and defaults to `PixelViewportPolicy()`, the canonical
  Contain + Integer + Center policy.
- The logical screen has exactly one configuration entry: `PixelHostProfilePolicy`.
  `PixelHostProfilePreference`, `PixelHostView.profilePreference` and
  `PixelHostSetupConfig.profilePreference` are removed in favor of
  `PixelHostProfilePolicy.AdaptivePixels`, and `PixelHostView.screenProfile` becomes a read-only
  value derived from the policy; pinning a grid now uses `PixelHostProfilePolicy.Fixed(profile)`.
- Focus has exactly one owner model: the runtime-local `PixelFocusOwner` held by each
  `PixelHostView` and `PixelTester`. The process-global `PixelFocusManager` facade (`rootScope`,
  `primaryFocus`, `setPrimaryFocus`, `clearFocus`, `dispatchKeyEvent`, `dispatchTextInputEvent`)
  is removed together with the detached legacy focus tree, the root-scope sentinel rebinding in
  `FocusScope`, and every `legacyOwner` fallback in `FocusNode` / `FocusScopeNode` / `Focus`.
  A `FocusNode` that is not mounted in a runtime returns `false` from `requestFocus()` and
  treats `unfocus()` as a no-op, so focus can never cross runtimes. `Focus(...)` is now a single
  canonical declaration whose `onTextInput` is an optional parameter instead of a second overload.
- Input events are exactly two disjoint kinds. `PixelKey.CHARACTER` and `PixelKeyEvent.character`
  are removed, so `PixelKeyEvent` expresses only navigation, activation, and dismissal, and
  `PixelTester.pressKey` no longer takes a character. Every printable payload — BMP,
  supplementary, combining sequence, or multi-code-point IME commit — travels as one
  `PixelTextInputEvent`. Text that no handler consumes no longer falls back to the key path, and
  the Android mapper reports `PixelKey.UNKNOWN` for key codes without a non-text meaning. DPAD,
  gamepad, hardware-keyboard, IME composition, surrogate, and grapheme behavior are unchanged,
  as are the Android API-level branches that back them.
- The glyph SPI is Unicode-scalar only: `GlyphSource.findGlyph(codePoint, style)` and
  `GlyphProvider.rasterizeGlyph(codePoint, style)`. The `Char` entry points, the default
  `rasterizeGlyph(Int, ...)` projection onto them, and the supplementary → U+FFFD compatibility
  fallback are removed; `CompositeGlyphProvider` and `BitmapGlyphSource` no longer bridge through
  `Char`. Both entry points still reject non-scalar keys, and malformed UTF-16 input still maps to
  one deterministic U+FFFD lookup while preserving source offsets — that is a robustness contract,
  not provider compatibility.
- Frame scheduling has exactly one contract: `PixelFrameScheduler.scheduleFrame` returns a
  `PixelFrameCallbackRegistration` that must physically remove a pending callback. The additive
  `PixelCancellableFrameScheduler` interface and the `scheduleCancellableFrame` extension with its
  guarded logical-cancellation fallback for old scheduler implementations are removed.
- `MediaQuery` carries only logical viewport metrics. `MediaQuery.capabilitiesOf` and
  `maybeCapabilitiesOf`, added to avoid widening `MediaQueryData`'s constructor/copy ABI, are
  removed in favor of `HostCapabilities.of` / `maybeOf`. `HostCapabilities.of` keeps its
  documented headless default for off-screen rendering and embedded hosts.
- Concise component APIs (`OutlinedButton`, `TextButton`, `Checkbox`, `Switch`, `ListTile`,
  `Dialog`, `BottomSheet`, `ConfirmDialog`, `ModalBarrier`, `Toast`, `Snackbar`, `ProgressBar`,
  `PixelLoadingBar`, `AnimatedPixelLoadingBar`, `Badge`, `Divider`, `AppScaffold`, `EmptyState`,
  `ShortcutHint`, `Scrollbar`, `RefreshIndicator`, `SwipeRefreshScaffold`, `TextField`, `Slider`,
  `ValueAdjuster`, `Menu`, `Dropdown`, `Tooltip`, `Slidable`, `SlidableAction`) are retained and
  delegate directly to the same state-aware token implementation. Their optional visual parameters are
  nullable with a `null` default, meaning "resolve from tokens"; explicit caller values keep exact
  precedence. The pre-release scope-less legacy visual branches are removed, so a concise call
  builds an identical widget tree with or without a `PixelTheme` provider and resolves
  `PixelThemeTokens.Default` when no provider is mounted.
- Optional accessibility names follow the same rule: every `semanticLabel` is nullable with a
  `null` default meaning "omitted". `NavigationBar` and `NavigationRail` no longer compare the
  caller value against a `"Navigation bar"` / `"Navigation rail"` default-string sentinel, so an
  explicit label always outranks the localization provider even when it equals the English
  fallback; an explicit blank collection name is still rejected at construction time.

### Pre-release convergence

The SDK has never been published or installed, so nothing in the repository needs to remain
compatible with an earlier binary, source tree, or on-device data. Every remaining structure that
existed only for that purpose is removed in favor of one canonical model:

- Persisted formats keep exactly one schema. The Navigator snapshot schema is
  `PixelNavigatorPersistentSnapshotSchemaVersion` (`1`); the schema-1 decode path, its test-only
  encoder, and the restore plan's source/current schema projection with `migratedFromOlderSchema`
  are removed. Any other envelope schema is still rejected as `UnsupportedSchema`. Resource
  manifest/catalog and sprite sheet/atlas each keep one protocol version
  (`PixelResourceManifestVersion`, `PixelSpriteSheetVersion`) and reject the rest; colors and fonts
  no longer require a second manifest version.
- Resource definitions are ordinary `data class` values again. The hand-written `componentN`
  accessors, the reduced-arity `copy` descriptors, and the checksum-less `hashCode` / `toString`
  branches of `PixelBitmapResourceDefinition`, `PixelSpriteSheetResourceDefinition` and
  `PixelFontResourceDefinition` are removed. `PixelResourceCacheSnapshot` counters are `Long`,
  matching the cache's internal accounting instead of saturating into an older `Int` surface.
- The Host environment has exactly one model and one override entry. `PixelHostView.textDirection`,
  `layoutDirectionOverride` and `motionSettingsOverride` are removed; `capabilitiesOverride` is the
  only override and `PixelHostView.hostCapabilities` publicly exposes the currently effective
  snapshot so a partial override is written as `hostCapabilities.copy(...)`.
  `PixelHostSetupConfig.textDirection` becomes `capabilitiesOverride`.
- ABI-only structures are collapsed into their canonical form: `PixelEngineModule`,
  `PixelHostFrameScope`'s constructor marker and duplicate constructors, `PixelEngine`'s
  post-construction theme marker, `MediaQueryData`'s three-argument constructor, and the
  descriptor-preserving `Popover` overload are removed.
- `PixelOverlayHandle` exposes only `dismiss(reason)`; the controller-local `id`, the no-argument
  handle dismissal and `PixelOverlayController.dismiss(id)` are removed. `PixelSemanticsNode.id` is
  a required positive value, so the retained-source and frame-index accessibility identity
  fallbacks are removed. `AxisBufferComposer.compose` requires a caller-owned `out` buffer.
- Robustness contracts are kept and simply described accurately: reverse selections and malformed
  UTF-16 in `PixelGraphemeBoundaryMap`, integer/fractional viewport quantization, pre-API-30
  `WindowInsets` splitting, and the platform-version branches behind motion settings, predictive
  back and accessibility projections are all current behavior, not history.
- `RenderPerfLogger` is internal implementation diagnostics rather than consumer API. Resource
  eviction-listener failures remain isolated from committed cache results but now emit a diagnostic
  event instead of disappearing silently.
- Ordinary render sessions publish their completed target lists directly and select modal ancestry
  without per-target temporary ancestor lists. Modal frames retain their required filtered copies.
- The required fast gate now enforces reviewed per-file Kotlin size budgets, a 220-line function
  ceiling, and the absence of deleted-module names or nine-module wording in governance files.

### Compatibility

- Android `minSdk` 24 and `minCompileSdk` 36.
- Minimum supported toolchain: AGP 8.10.1, Gradle 8.11.1, Kotlin 2.2.10, and Java 11.
- `1.0.0` is the first published line, so there is no earlier release to stay compatible with and
  no released API baseline in the repository yet.

### Known limits

- The SDK is Android-first. Compose wrappers are intentionally excluded from the core artifact;
  Compose applications may host the Android View surface from application code.
- Representative device performance baselines and performance soak remain a separate follow-up
  workstream and are not part of this release candidate's acceptance scope.

## Pre-release baselines

`0.1.0-SNAPSHOT` was an internal development baseline. It was never published or distributed, so no
consumer can hold a binary or persisted state produced by it. Its per-milestone breaking-change and
JVM-descriptor narrative is therefore not maintained here; the current behavior of every SDK
surface is documented in [使用说明与API手册](使用说明与API手册.md),
[架构与设计](架构与设计.md), and the [接入与升级指南](guides/migration.md).

### Compatibility policy before the first release

Until `1.0.0` is published there is no cross-version compatibility promise and no released API
baseline. Every intentional API change must update the reviewed current source, Metalava, and JVM
ABI baselines under `pixel-engine/api/` in the same change. The released-signature compatibility
gate is introduced with the first formal release, as described in
[发布与维护](发布与维护.md#11-released-baseline).
