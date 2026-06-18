# pixel-engine 0.1.0-SNAPSHOT Release Notes

## Status

This is the current local SDK release-candidate snapshot. It is not a remote
Maven release, Git tag, or GitHub release.

## Highlights

- Flutter-style retained widget runtime with `PixelNavigator`, route lifecycle,
  route results, route stack snapshot/restore, deep link replacement, and route
  scroll restoration buckets.
- Stabilized host input path with `PixelHostGestureRouter`, nested scroll
  handoff, TextField routing, slider ownership, scrollbar/refresh routing, and
  pure Kotlin gesture tests.
- TextField improvements: multiline caret/selection/composition painting,
  cursor blink, word selection, selection handles, IME actions, clipboard edit
  actions, and readonly/disabled behavior coverage.
- Rendering and graphics additions: alpha blending, `Opacity`,
  `ClipRect`, `Transform.translate`, `Sprite`, `AnimatedSprite`, `Polygon`,
  and `Path`.
- Layout and scrolling additions: `GridViewBuilder`, `Wrap`, constraints
  widgets, `Scrollbar`, `RefreshIndicator`, `CustomScrollView`, pinned header,
  sliver app bar, scroll anchor restoration, and Bundle restoration helpers.
- Resource system additions: Android PNG loaders, sprite sheet JSON loader,
  resource manifest parsing, glyph pack converter tests, and per-owner
  `PixelResourceCache`.
- Component library additions: `ListTile`, `Icon`, `Checkbox`, `Switch`,
  `Dialog`, `Toast`, `Snackbar`, `Tabs`, `SegmentedControl`, `ProgressBar`,
  `ActivityIndicator`, `Badge`, `Divider`, `Gap`, and `AppScaffold`.
- Test and release engineering additions: WidgetTester DSL, golden and element
  snapshots, fuzz and soak coverage, render performance smoke report, public API
  baseline, binary API baseline, KDoc coverage gate, Maven local dry-run,
  minimal sample, MkDocs site, and CI workflow.

## New Public API Areas

- Navigation: `PixelNavigator`, `PixelRoute`, `PixelRouteTransition`,
  `PixelNavigatorState`, route results, deep link helpers, Bundle snapshot
  helpers, and `PixelRouteScrollRestoration`.
- Graphics: `PixelBitmapRegion`, `PixelSpriteSheet`, `Sprite`,
  `AnimatedSprite`, `PixelPoint`, `Polygon`, `PixelPath`, `Path`, `Opacity`,
  `ClipRect`, and `Transform.translate`.
- Inputs and forms: `PixelTextFieldStyle` blink/handle controls,
  `PixelTextEditAction`, `FocusNode`, `FocusScope`, `FocusTraversalPolicy`,
  `Form`, `FormField`, async validation, and submit state helpers.
- Layout and scrolling: grid, wrap, constraint widgets, scrollbar, refresh
  indicator, custom scroll/sliver widgets, list anchor restoration, and
  Android Bundle state helpers.
- Components: selection controls, overlay feedback widgets, status/navigation
  controls, and app scaffold primitives.
- Resources: bitmap loaders, sprite sheet loader, resource manifest parser,
  glyph pack loader/converter, and resource cache.
- Debugging/testing: `PixelTester`, finder DSL, inspector snapshots, target
  snapshots, debug overlays, and render/tree dump helpers.

## Behavior Changes

- `pixel-engine` release validation is scoped to the SDK, `pixel-demo`, and
  `pixel-minimal-sample`; Launcher app validation is no longer part of the
  `pixelReleaseCheck` gate.
- `HostRootWidget` now separates system `windowInsets` from IME `viewInsets`.
  `MediaQuery.viewPadding` reflects system bars, `MediaQuery.viewInsets`
  reflects IME overlap, and `MediaQuery.padding` is the non-overlapped safe
  padding.
- Transparent and semi-transparent pixels now use the engine blending path
  instead of relying on widget-specific behavior.
- TextField selection/composition painting is line-aware rather than a single
  whole-string approximation.
- `PixelTester.pumpAndSettle` observes active tickers, pending build work, and
  scroll activity instead of relying only on a fixed frame count.

## Migration Notes

- Apps using `PixelHostView.windowInsets` for both system bars and IME should
  move IME-specific layout to `MediaQuery.viewInsets`.
- Demo-only navigation stacks should migrate to `PixelNavigator` when route
  lifecycle, results, or restoration are needed.
- Tests that directly invoked callbacks should prefer the WidgetTester finder
  and coordinate event APIs.
- Resource loading should go through `PixelBitmapAssetLoader`,
  `PixelBitmapResourceLoader`, sprite sheet loader, or `PixelResourceCache`
  instead of ad hoc bitmap construction when loading Android assets.

## Validation

Run the local release gate:

```bash
./gradlew pixelReleaseCheck --no-daemon
```

The gate includes:

- `:pixel-engine:checkPublicApi`
- `:pixel-engine:checkBinaryApi`
- `:pixel-engine:checkKdocCoverage`
- `:pixel-engine:testPixelGlyphPackConverter`
- `:pixel-engine:testDebugUnitTest`
- `:pixel-engine:lintDebug`
- `:pixel-engine:assembleDebug`
- `:pixel-demo:assembleDebug`
- `:pixel-demo:testDebugUnitTest`
- `:pixel-minimal-sample:assembleDebug`
- `:pixel-engine:publishToMavenLocal --dry-run`
- `mkdocs build --strict`

Review the generated read-only performance report:

```bash
bash tools/pixel-perf-smoke.sh
```

```text
pixel-engine/build/reports/perf/pixel-engine-render-smoke.txt
```

Do not push a remote Maven artifact, tag, or GitHub release from this snapshot
without a separate release approval.
