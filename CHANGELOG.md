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

- `TextButton` and `PixelTextButtonStyle`.
- `Slidable`, `SlidableAction`, and `SlidableActionPane`.
- `PixelHostBridge.updateTextInput`.
- `PixelListController.isAtEnd`.

### Compatibility policy

`0.x` source and binary compatibility is best-effort. Any intentional breaking change must be
listed here and must update the public/binary API baselines in the same change.
