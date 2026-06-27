# Changelog

## 0.1.0-SNAPSHOT

Internal 0.x SDK baseline for `pixel-engine`.

### SDK status

- `pixel-engine` is published as `com.purride:pixel-engine:0.1.0-SNAPSHOT`.
- Public and binary API baselines are tracked under `pixel-engine/api/`.
- The release gate is `tools/pixel-release-check.sh`.

### Breaking changes recorded in this baseline

- `PixelHostView.backgroundColor` was renamed to `bezelColor`.
- `PixelHostView.pixelGridColor` was renamed to `offPixelColor`.
- `PixelPagerController.startDrag` now requires `viewportSizePx`.

### Added SDK surface

- `PixelErrorBoundary` and `PixelErrorPanel` for build/render-time widget fallback.
- `PixelBackDispatcher`, `PixelBackHost`, `PixelBackHandler`, and `PixelHostView.handleBackPressed`.
- `PixelHapticFeedback` for widget-level host haptic requests.
- `PixelHostView.dispose` and `PixelHostSetup.dispose` for explicit host teardown.
- `PixelOverlayController`, `PixelOverlayHost`, and overlay handles for toast/dialog/snackbar.
- `PixelTester`, `PixelFinder`, and `PixelTestGesture` as SDK testing APIs.
- `PixelTheme`, `PixelThemeData`, and `PixelThemeColors` for widget-level pixel defaults.
- `Visibility` for simple child/replacement switching.
- `LoadStateView` for `PixelAsyncSnapshot` loading/empty/error/content rendering.
- `SelectionList` and `OptionList` for controlled single-choice lists.
- `ValueAdjuster` and `Stepper` for controlled pixel value editing.
- `TextButton` and `PixelTextButtonStyle`.
- `EmptyState` and `ConfirmDialog` feedback components.
- `Slidable`, `SlidableAction`, and `SlidableActionPane`.
- `PixelHostBridge.updateTextInput`.
- `PixelListController.isAtEnd`.
- `PixelPagerSavedState` / `PixelTextFieldSavedState` Android `Bundle` helpers.
- Android accessibility bridge backed by the existing semantics tree.

### Compatibility policy

`0.x` source and binary compatibility is best-effort. Any intentional breaking change must be
listed here and must update the public/binary API baselines in the same change.
