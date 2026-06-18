# Release Checklist

The tracked release checklist lives at the repository root:

- [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md)

Release validation must include:

- `./gradlew :pixel-engine:checkPublicApi --no-daemon`
- `./gradlew :pixel-engine:checkBinaryApi --no-daemon`
- `./gradlew :pixel-engine:checkKdocCoverage --no-daemon`
- `./gradlew :pixel-engine:testDebugUnitTest :pixel-engine:lintDebug :pixel-engine:assembleDebug :pixel-demo:assembleDebug :pixel-demo:testDebugUnitTest --no-daemon`
- `./gradlew :pixel-minimal-sample:assembleDebug --no-daemon`
- `./gradlew :pixel-engine:publishToMavenLocal --dry-run --no-daemon`
- `mkdocs build --strict`

The engine unit test gate also writes a read-only render smoke report to
`pixel-engine/build/reports/perf/pixel-engine-render-smoke.txt`. Review it for
obvious trend regressions; do not commit generated perf reports.
