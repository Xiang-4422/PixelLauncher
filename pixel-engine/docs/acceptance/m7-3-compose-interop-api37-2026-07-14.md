# M7-3 Compose 互操作适配验收

- 日期：2026-07-14
- 工作包：M7-3
- 当前结论：已完成；实现、专项门禁、API 37 模拟器测试和未删项统一发布门禁全部通过
- 设备：`emulator-5554`，`Pixel_4` AVD，Android 17 / API 37，16KB page size

## 实现范围

`pixel-compose` 从 internal 空锚点升级为公开 `PixelHost` Composable。wrapper 复用标准
`PixelHostSetup`，没有复制渲染、输入或 accessibility 后端；`PixelComposeHostContainer` 负责
WindowInsets 转发和 onRelease 释放。Compose `rememberSaveable` 作为 saved-state fallback 注入，
Engine 显式服务保持优先。`pixel-compose-sample` 是独立可构建、可 R8 的最小应用。

公开契约明确只接受 `() -> Widget`，不接受 `@Composable` child，因此任意 Compose composable
嵌入 retained pixel tree 属于明确不支持范围。

## 模拟器互操作证据

显式执行：

```text
adb -s emulator-5554 get-state -> device
adb -s emulator-5554 shell getprop ro.build.version.sdk -> 37
adb -s emulator-5554 shell getprop ro.product.model -> sdk_gphone16k_arm64
ANDROID_SERIAL=emulator-5554 ./gradlew :pixel-compose:connectedDebugAndroidTest --no-daemon
```

结果为 2 tests、0 failures、0 errors、0 skipped，耗时 1.687 秒。覆盖 ViewTree lifecycle、density、
四边 WindowInsets、Android focus、精确文本输入、真实 IME bridge、virtual accessibility subtree、
Activity recreate 后 saved-state 恢复、旧 Host destroy，以及状态存储防御性复制和上限拒绝。

- XML：`pixel-compose/build/outputs/androidTest-results/connected/debug/TEST-Pixel_4(AVD) - 17-_pixel-compose-.xml`
- SHA-256：`da32e662fde3ff0b1b13173697991614108983b1e41989dd08d3880b1cb7b5ac`

同一模拟器随后执行 `ANDROID_SERIAL=emulator-5554 ./gradlew
:pixel-engine:connectedDebugAndroidTest --no-daemon`，普通 Host 聚合回归为 67 tests、0 failures、
0 errors、0 skipped，耗时 61.833 秒；XML SHA-256 为
`7c2ff1ac3c6c8c6d1d6e45636f29d3f97d60d41ce683ffcae9c77ae43e1aab2f`。这证明 Engine fallback
与 Setup 调整没有让非 Compose Host 路径回归。

本次设备操作只针对 `emulator-5554`，没有向实体设备安装或启动应用。

## API、预算与消费者

- Compose Metalava：`b1f7647fcc19c1df1edfae4452a250cc2c3c4ed5978eb069fc635e188bee59f9`
- Compose JVM ABI：`7b7dde47c596c0af713a0f4b92f984a75c1dbb5b4983482676460cf4bd04f097`
- Android Metalava：`e5e2c61cd6d1f6a3b1ca4062951a40d64b8792d40668298a25b1a8192e5112e3`
- Android JVM ABI：`950da21c80363b48409b99a4d211d866f5730b89bc9eb6169894e6eccf194365`
- `pixel-compose-release.aar`：16,797 bytes、5 classes、31 methods，SHA-256
  `8b66f360e0e8f4ec8c0ca4959aa067f82af45fb0fa1bb83399e189283a036c57`
- 发布直接依赖 9 项、解析后 runtime artifact 67 项，均由精确集合预算冻结。

独立外部消费者只声明发布 `pixel-compose` 坐标，应用 Compose compiler，真实编译 `PixelHost`、
单测、Debug APK 和 Release R8。依赖报告包含 runtime/ui 1.11.3 与五个最小 Pixel 能力 artifact，
不包含 legacy `pixel-engine`、testing 或 debug。反向 ownership/API/KDoc 门禁证明非 Compose 最小图
仍不包含 Compose。

## 最终发布门禁

显式设置 `ANDROID_SERIAL=emulator-5554` 后执行未删项 `bash tools/pixel-release-check.sh`，退出码为
0。完整 API/ABI、stable boundary、ownership、KDoc、全部模块单测/lint/Release、Compose sample
Debug/Release R8、secret/backup、RenderObject SPI、RouteEntry、旧二进制以及全部隔离 Maven/R8
消费者均通过。JVM 六场景性能趋势 `overallPassed=true`，soak 与工具测试通过，Benchmark target
Release/Benchmark R8 和两个 APK 的 Baseline Profile 打包通过，严格 MkDocs 成功。

最终 Compose AAR 仍为 16,797 bytes、5 classes、31 methods，SHA-256 为
`8b66f360e0e8f4ec8c0ca4959aa067f82af45fb0fa1bb83399e189283a036c57`；没有通过放宽预算、删除
失败测试或把 Compose 引入非 Compose artifact 来取得门禁结果。M7-3 范围内 P0/P1 遗留为零。
