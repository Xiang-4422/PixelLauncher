# M7-2 PixelEngine 实例、Services 与 Host capability 验收

- 日期：2026-07-14
- 工作包：M7-2
- 当前结论：已完成；实现、专项测试、API 37 全量模拟器测试与统一发布门禁全部通过
- Android 设备：`emulator-5554`，`Pixel_4` AVD，Android 17 / API 37，16KB page size

## 实例与所有权边界

`PixelEngine.Builder` 替代空 marker 成为可运行实例入口。实例不可变，服务集中在
`PixelEngineServices`；Host 动态读取当前绑定 Engine，因此切换实例不需要重建 retained runtime。

| 能力 | 发布所有权 | 实现结果 |
|---|---|---|
| `PixelEngine`、Builder、resource resolver | `pixel-android` | 默认缓存按每次 build 隔离；所有服务可注入 |
| clock、logger、error event/reporter | `pixel-runtime` | 平台中立，可在纯 JVM 使用 |
| frame scheduler、ticker factory | `pixel-runtime` contract + `pixel-android` 默认实现 | 每 Host 私有 scope/provider |
| 聚焦 Host capability 与 typed action | `pixel-runtime` | 缺失/失败/成功结果封闭 |
| resource cache | `pixel-core` | 默认实例独占，可由调用方显式共享 |
| 旧 `PixelEngineModule` / `PixelHostBridge` | legacy compatibility | 保留旧 descriptor，不参与新实例状态所有权 |

ownership 门禁当前覆盖 271 个生产文件和 32 条无环声明依赖，finding 为 0。冻结 package
`com.purride.pixelengine` 的 split ownership 已在 manifest 中写明：实例 API 属于 Android Host
artifact，旧 marker 只留在聚合 artifact。

## 服务注入与隔离

Builder 覆盖 clock、frame scheduler、ticker provider factory、error reporter、resource resolver、
resource cache、logger、完整 Host 环境覆盖、聚焦 Host capability 和 theme。未传 cache 时，每次
`build()` 都创建新缓存；只有显式传同一对象才共享。

默认 `engine.theme` 可读取完整 `PixelThemeTokens.Default`，但 Host 只在调用方显式 `.theme(...)`
时安装根主题 provider。这同时满足新 Engine 主题隔离和旧 scope-less facade 的逐像素兼容。全量设备
测试首次发现默认主题作用域改变旧 ProgressBar fixture；修复后定向用例与全量 67 项均通过，没有
更新 golden 掩盖回归。

`PixelEngineTest` 覆盖两套主题、fake clock、手动 scheduler、cache、resolver、Host 环境和 system
action；同一 Builder 连续 build 的 cache 也不共享。`PixelEngineServicesInstrumentedTest` 在同一
Activity 同时挂两个真实 `PixelHostView`，验证主题像素、时钟、cache、scheduler、ticker provider、
Host 环境、resolver 和 typed action 不串扰。

## Host capability 与动作协议

旧聚合桥被拆成 IME、clipboard、haptic、back、accessibility、saved state 和 system action 七个
聚焦接口。`PixelHostCapabilitySet` 使用 Engine-first、legacy bridge fallback 合并规则；旧桥接口与
JVM 描述符没有变化。

| 场景 | 结果 |
|---|---|
| capability 缺失 | `PixelCapabilityResult.Unsupported` |
| 返回值 capability 缺失 | `PixelCapabilityValueResult.Unsupported` |
| 支持但没有剪贴板/状态内容 | `PixelCapabilityValueResult.Value(null)` |
| capability 抛异常 | `Failed(capability, cause)`，异常不穿透渲染链 |
| typed action | URI、返回、应用设置、权限四类封闭 action，不由新代码拼字符串 |

`PixelHostCapabilitySetTest` 3/3 覆盖空集合、四类 typed action 和异常隔离；同一测试也进入公共 API
覆盖门禁。

## 结构化错误

`PixelUiRuntime` 的兼容构造器保留，并增加 clock/reporter/logger 注入。Build、render 和 fallback
路径在恢复结果已知后发送 `PixelErrorEvent`，保留原始 cause、阶段、widget/element/render 上下文、
恢复结果、Engine 单调时间和稳定属性。Reporter/logger 自身失败不会掩盖原异常。

纯 JVM 的 `PixelUiRuntimeServicesTest` 4/4 覆盖：build 已恢复、render 已恢复、无边界 build 错误、
reporter 二次失败。测试只使用 fake clock 和 runtime primitive，不读取 Android 服务。

## 发布 API、兼容与外部消费者

新增 API 已分别进入 `pixel-runtime`、`pixel-android` 和聚合 baseline。`PixelHostBridge` 的旧方法、
`PixelHostFrameScope(PixelFrameScheduler)` 构造器和旧 setup 工厂保留；`PixelEngineModule` 仅增加弃用
提示。stable API boundary、Metalava、JVM ABI、KDoc、artifact ownership 和旧二进制 runner 已通过。

隔离 `pixel-android` Maven 消费者现在真实编译 `PixelEngine.Builder`、外部 `PixelClock` 实现、
`ManualFrameScheduler` 和带 Engine 的 setup 重载，并通过 JVM 测试、Debug APK 与 Release R8。
依赖图仍不包含 `pixel-engine`、testing、debug、Compose 或 `androidx.compose`。runtime 与完整 SDK
隔离消费者也已通过。

## API 37 模拟器证据

设备预检与执行均显式指定模拟器：

```text
adb -s emulator-5554 get-state                         -> device
adb -s emulator-5554 shell getprop ro.build.version.sdk -> 37
adb -s emulator-5554 shell getprop ro.product.model     -> sdk_gphone16k_arm64
ANDROID_SERIAL=emulator-5554 ./gradlew :pixel-engine:connectedDebugAndroidTest --no-daemon
```

最终结果：67 tests、0 failures、0 errors、0 skipped，耗时 61.969 秒。机器结果：

- `pixel-engine/build/outputs/androidTest-results/connected/debug/TEST-Pixel_4(AVD) - 17-_pixel-engine-.xml`
- SHA-256 `02da637d2660a07a9fb7b15076ec5872aaf56444a405b99157d88b210af153df`

本次设备操作只针对 `emulator-5554`，没有向实体设备安装或启动应用。

## 最终门禁

最终未删项执行 `bash tools/pixel-release-check.sh`，退出码为 0。门禁通过全部 Gradle 编译、测试、
双变体 lint、Release、API/ABI、KDoc、ownership、secret/backup、RenderObject SPI、RouteEntry、旧
二进制运行时、完整 SDK 与 core/runtime/widgets/navigation/android/testing/debug/compose 隔离消费者、
JVM perf、soak、两个 APK 的 Baseline Profile 和严格 MkDocs。`pixel-android` 隔离消费者额外编译并
执行真实 `PixelEngine.Builder`、外部 fake clock、`ManualFrameScheduler`、Engine setup 重载和 Release
R8；其依赖图没有 legacy aggregate、testing、debug 或 Compose。

最终发布预算与产物如下：

| 产物 | bytes | classes | methods | resolved runtime artifacts | SHA-256 |
|---|---:|---:|---:|---:|---|
| `pixel-runtime-release.aar` | 1,190,637 | 614 | 5,287 | 3 | `1fd35e59a0deb4119cd9df7d4c30ca403f40453b50bec6d3744456792556eddd` |
| `pixel-android-release.aar` | 402,672 | 185 | 1,830 | 21 | `1fd2b108db1885bad69b2a737a402eb8a148bffd40d302b4a255fd61d6e66a7b` |
| legacy `pixel-engine-release.aar` 本体 | 41,605 | 1 | 2 | — | `a36d04f3522c451a830a86a4a03f2874934a1443089bdd2bdc73e91a76e07f4b` |
| legacy 八 AAR 兼容并集 | 3,463,941 | 1,581 | 15,700 | 24 | 重复 class 为 0 |

新增表面进入 reviewed baseline，最终 SHA-256 为：runtime Metalava
`aaecef8ff5f4ca5e90af9531b1f07739f54a5d928976e42d4fbb7cdfb4d367b5`、runtime JVM ABI
`e4e9f550ca8ebc40f16061a72e89d97f1ae7d9111556b9048db398305a7e37e7`、Android Metalava
`fc719c8358294208d6b10eae564b882a1e284998393c9ca263103584c60ef416`、Android JVM ABI
`46b30cbad533fdcc22f8a9652f6dc6c4942c2133ca83f0d7635cc77cff948dd7`。聚合 public/Metalava/JVM
ABI 分别为 `500d756af3c196e791b53bdbbac7c591dec4a6f5202982aaa7e739c18518d9b1`、
`ea9ef246e7e4f78f20d5834a2f751f131993cdf279d7769c1bd8da4f2652ed76`、
`dc0fd674bf61def4484f74e37b9bae6f38022393d4642892a3a8529d03909417`。

## 验收结论

M7-2 的六项任务与三项验收条件全部完成。多 Engine/多 Host 不串扰、纯 JVM fake service 覆盖和
缺失 capability 的明确降级均有自动化证据；范围内没有遗留 P0/P1。下一工作包为 M7-3 Compose
互操作适配，整个 1.0 Goal 继续保持 active。
