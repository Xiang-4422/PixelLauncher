# M7-1c `pixel-widgets` Artifact 拆分阶段验收

- 日期：2026-07-14
- 结论：阶段通过；M7-1 总工作包继续进行中
- Android 行为设备：`Pixel_4` AVD，Android 17 / API 37

## Artifact 与源码所有权

- 新增独立 `:pixel-widgets` Android Library 与 `com.purride:pixel-widgets:0.1.0-SNAPSHOT` 发布坐标。
- widgets 从兼容源码树按受审 ownership 清单确定性同步 60 个生产 Kotlin 文件，覆盖标准组件、主题、Overlay、滚动、viewport 与声明式动画。
- `NavigationControls.kt` 保留原公开包名和类名，但精确 owner 改为 `pixel-navigation`；该文件直接依赖多栈 Navigator，若归入 widgets 会形成 widgets→navigation→widgets 环。
- 生产 `pixel-engine` 不使用 Kotlin friend path，只通过 `api(project(":pixel-widgets"))` 聚合。历史白盒单测仅在 test compile 使用 core/runtime/widgets Debug JAR friend path。
- `AutomaticFocusAction`、`InteractionDetector`、`mergeControlStates` 和 `FocusableControl` 以 `PixelArtifactInternalApi` 标记为兄弟 artifact SPI；独立与聚合 Metalava/JVM ABI 均隐藏它们，消费者稳定 API 没有扩大。

ownership 报告覆盖 265 个 Kotlin/Java 生产文件、9 个目标 artifact 与 33 条声明依赖，finding 为 0；widgets 精确拥有 60 个文件。

## API、ABI、POM 与预算

独立 widgets 门禁：

- Metalava baseline：`pixel-widgets/api/pixel-widgets.metalava-api`，1,594 行，SHA-256 `6a254dfadbd72bf49059f3d22eb76b73d769c7830561ecf23d62425fa6266f24`
- JVM ABI baseline：`pixel-widgets/api/pixel-widgets.binary-api`，1,595 行，SHA-256 `6eed4a86289d786eecd84ebbccf2009105d088d215ad6709cf26d18292e683ca`
- Release AAR：1,130,374 / 1,250,000 bytes，451 / 520 classes，5,509 / 6,200 methods
- AAR SHA-256：`66084e94285da74b9eb2a9f47a8c9b69c549a142570ebc53a442f05439b58b99`

独立源码 API 和 JVM ABI 均是聚合 baseline 的严格子集；内部 sibling SPI、navigation、Android、testing、debug 与 Compose 类型没有泄漏。

POM 只发布：

- `com.purride:pixel-core:0.1.0-SNAPSHOT`
- `com.purride:pixel-runtime:0.1.0-SNAPSHOT`
- `org.jetbrains.kotlin:kotlin-stdlib:2.2.10`

Gradle Release runtime 实际解析集合只额外包含 `org.jetbrains:annotations:13.0`。它不传递聚合 engine、navigation、Android、Lifecycle、testing、debug 或 Compose。

旧聚合坐标按 engine/core/runtime/widgets 四个 AAR 并集计数：3,387,894 / 3,500,000 bytes、1,525 / 1,600 classes、15,341 / 16,000 methods，重复 class 为 0。engine AAR 本体为 881,493 bytes，5 个发布依赖和 20 个解析 artifact 与精确白名单一致。

聚合 JVM ABI 生成器同时修复一个既存盲区：无成员的公开 class/interface 也必须记录 class 存在性。本次补记 15 个此前遗漏的空公开类型；生产 classfile 没有改变。

## 行为、消费者与兼容验证

JVM 回归：

- `:pixel-widgets:testDebugUnitTest`：1/1，使用真实 `PixelUiRuntime` 完成标准 `DecoratedBox` + `Text` 布局与黑白像素绘制
- `:pixel-engine:testDebugUnitTest`：1,178/1,178
- 两组均为零失败、零错误、零跳过

隔离 widgets 消费者只声明临时 Maven 仓库中的 `com.purride:pixel-widgets`，通过公开组件工厂构建 retained 树；依赖报告精确确认 core/runtime/widgets，并拒绝 engine/navigation/android/testing/debug/compose/Lifecycle。JVM 行为测试、Debug APK 和启用 R8 的 Release APK 全部成功。

以下发布边界全部通过：

```bash
./tools/pixel-widgets-consumer-smoke.sh
./tools/pixel-sdk-consumer-smoke.sh
./tools/pixel-render-spi-compatibility.sh
./tools/pixel-route-entry-compatibility.sh
./tools/pixel-previous-binary-compatibility.sh
```

旧二进制 runner 校验当前 engine/core/runtime/widgets 四个生产 AAR 的精确 SHA-256；冻结旧 engine AAR 不在 runtime classpath，旧消费者没有内嵌 SDK class，当前报告记录 21 个 runner artifact。

更新后的统一 `tools/pixel-release-check.sh` 未删项完整通过，覆盖 secret/backup、core/runtime/widgets/engine API 与 ABI、76 项 Python tooling、全部 JVM/Lint/Release 构建、六条隔离消费者/兼容链、六场景 JVM perf 趋势、soak、两个消费者 Baseline Profile APK 和 `mkdocs build --strict`。

## API 37 模拟器验证

命令显式指定模拟器序列号，没有向同时连接的实体设备安装或启动应用：

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :pixel-engine:connectedDebugAndroidTest --stacktrace
```

结果：66/66，零失败、零错误、零跳过。机器结果：

- `pixel-engine/build/outputs/androidTest-results/connected/debug/TEST-Pixel_4(AVD) - 17-_pixel-engine-.xml`
- SHA-256 `e4fa29f40ff261b477fa05e18d46b7a1f723e3ce3397e4312864a451d4974229`

## 阶段结论与遗留

`pixel-widgets` 已满足独立 artifact 的实现、生产隔离编译、测试、API、ABI、POM、consumer rules、预算、最小消费者、历史兼容和模拟器行为回归要求，可作为 M7-1 的第三个完成阶段。

M7-1 尚不能整体完成：navigation/android/testing/debug/compose 仍需按 ownership graph 拆分，当前聚合 engine 仍包含这些实现；最小 Host 与聚合 debug 泄漏总验收保持未勾选。下一阶段拆 `pixel-navigation`，M7-1 与 Codex Goal 继续 active。
