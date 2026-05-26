# pixel-engine Release Checklist

## Versioning

- Use SemVer before the first public SDK release: `0.x` may change source compatibility, but every breaking change must be called out.
- After `1.0.0`, increment:
  - `MAJOR` for source or binary incompatible public API changes.
  - `MINOR` for backwards-compatible public API additions.
  - `PATCH` for bug fixes, docs, tests, and internal-only changes.

## Required Gates

- `./gradlew pixelReleaseCheck --no-daemon`
- `./gradlew :pixel-engine:checkPublicApi --no-daemon`
- `./gradlew :pixel-engine:checkBinaryApi --no-daemon`
- `./gradlew :pixel-engine:testDebugUnitTest :pixel-engine:lintDebug :pixel-engine:assembleDebug :pixel-demo:assembleDebug :pixel-demo:testDebugUnitTest --no-daemon`
- `./gradlew :pixel-minimal-sample:assembleDebug --no-daemon`
- `./gradlew :pixel-engine:publishToMavenLocal --dry-run --no-daemon`
- `mkdocs build --strict`

## Review Items

- Public API baseline reviewed and intentionally updated.
- Binary API baseline reviewed and intentionally updated.
- New public widgets are covered by docs, tests, and demo scenes.
- Golden or snapshot changes are reviewed as visual changes, not accepted blindly.
- Performance baselines are read-only unless a separate perf review approves updating them.
- Release notes include migration notes for any source compatibility change.
- No remote Maven publish, tag, or GitHub release is performed from local dry-run validation.
