# M5-3A 平台中立 Host 环境模型与 API 37 验收

## 结论

2026-07-13，M5-3A 已完成。SDK 新增平台中立、不可变且具备值语义的 Host capability
模型，覆盖 locale、layout direction、text scale、高对比度、motion、density、可空 refresh
rate、逻辑 display feature 和 width/height window size class。公开 capability 签名不依赖
`android.*`、`LocaleList`、Android `Rect` 或 AndroidX Window 类型。

Host 根环境从同一份 `HostCapabilitiesData` 派生 `HostCapabilities`、`Directionality` 和
`PixelMotionScope`。相等 snapshot 不通知依赖者；八个字段逐项变化时，订阅者各重建一次并
观察完整新 snapshot，retained State 身份保持。真实 API 37 Host 在 pause 和 detach 期间连续
接收三个 override 时不会发布中间值，resume 或 reattach 后只发布最终 snapshot。

M5-3A 范围内没有 P0/P1 代码、测试、文档或兼容遗留。Android 自动能力采集、viewport、
inset 重算、textScale 消费和 RTL Flex 属于 M5-3E；Localization bundle 与 locale fallback
属于 M5-3B；本报告不以手工 override 冒充这些后续工作。

## 公开模型与不变量

`HostCapabilities.kt` 提供：

- `PixelLocale`：严格校验并公开 canonical BCP-47 language tag；默认值为 `en`；
- `PixelLogicalRect`：边和派生宽高必须有限，允许零宽/零高 fold，不允许反向边；
- `PixelDisplayFeature`、`PixelDisplayFeatureType`、`PixelDisplayFeatureState`：使用逻辑坐标和
  显式 `UNKNOWN`，不保存平台对象；
- `PixelWindowSizeClass`：width 使用 600dp/840dp，height 使用 480dp/900dp 边界；
- `HostCapabilitiesData`：locale 列表非空且 canonical tag 不重复，text scale/density 必须为
  有限正数，refresh rate 为 `null` 或有限正数；列表输入 defensive copy 后以不可修改视图暴露；
- `HostCapabilities`：相等完整值不通知，`maybeOf` 区分未安装 provider，`of` 提供稳定默认值。

`HostCapabilitiesData` 故意不是 data class。手写 `copy` 避免 generated component 暴露调用方
传入的可变列表，同时 `equals`、`hashCode` 和 `toString` 覆盖全部能力字段。Java consumer 已
实际验证 full constructor、getter、full copy、Companion window-class resolver 和列表不可修改。

## Host、生命周期与 retained 行为

`PixelHostView.capabilitiesOverride` 是完整 snapshot override。非空时它对旧
`textDirection` 和 `motionSettingsOverride` 具有原子优先级；旧属性的新值仍被保存，但不会
为当前被覆盖的环境安排冗余帧，清除完整 override 后再生效。兼容路径缓存默认 snapshot，避免
每帧复制 locale/display-feature 列表。

`HostRootWidget` 注入同一 snapshot，并由它派生 direction 与 motion。新增的 capability
Inherited 边界只对“完全相同的 child 实例”使用定向 identity fast path：相等 snapshot 不沿子树
传播配置更新，distinct snapshot 仍通过 dependency registry 精确重建订阅者。首次全门禁曾发现
把该优化泛化到所有 Widget 会破坏既有 focus-scroll 的逐帧契约；最终实现已收窄到新
`HostCapabilities` 边界，`PixelFocusTest` 全类 9/9 和 retained 回归均通过。

API 37 的 `PixelHostLifecycleInstrumentedTest` 5/5 覆盖：

- pause 后依次写入 1.25x、1.5x、最终 2x/RTL/highContrast，并在每次写入后强制 draw；暂停期
  不重建，resume 后只出现初始值和最终值；
- detach 后依次写入 1.2x、1.6x、最终 2.25x/RTL/highContrast；owner 仍为 Resumed，reattach
  后只出现最终值；
- 两条路径均保持同一 State，且只在 ActivityScenario 终结时 dispose 一次。

设备为 `emulator-5554`、API 37 / Android 17、`sdk_gphone16k_arm64`，fingerprint 为
`google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys`。

## ABI 与独立消费者

环境能力通过 additive scope/accessor 扩展：

- `MediaQuery.maybeCapabilitiesOf` / `MediaQuery.capabilitiesOf`；
- `PixelHostView.capabilitiesOverride`；
- 新增 capability value/provider 类型。

没有修改 `MediaQueryData`、`ScreenProfile` 或 `PixelHostSetupConfig` 的既有构造、copy、
component 或 JVM descriptor。相对 M5-2 最终 baseline，reviewed diff 为 public API +42/-0、
binary API +118/-0、Metalava +121/-0；新增 capability 签名专项反射测试与 baseline 扫描均为
零 Android/AndroidX Window 类型泄漏。三份 baseline、`checkStableApiBoundary` 和冻结旧二进制
consumer 全部通过。

`tools/pixel-sdk-consumer-smoke.sh` 从隔离 file-Maven 仓库解析发布 POM/AAR：

- Kotlin consumer 在 `PixelTester` 中安装 `HostCapabilities`，通过 additive MediaQuery accessor
  读取中文 locale、RTL、textScale、contrast、density 与 refresh；
- Java consumer 使用完整 Java descriptor 构造、读取和复制 snapshot，解析 window size class，
  并验证 locale 列表不能被 Java 修改；
- consumer JVM 测试和独立 Android APK 组装同时通过。

## 最终验证

完整 `tools/pixel-release-check.sh` 成功，包含：

| 范围 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` JVM | 1132 | 0 | 0 | 0 |
| `pixel-demo` JVM | 26 | 0 | 0 | 0 |
| Launcher app JVM | 328 | 0 | 0 | 0 |
| Python tooling | 33 | 0 | 0 | 0 |
| API 37 Host lifecycle | 5 | 0 | 0 | 0 |

其中 M5-3A 三个新 JVM suite 为 16/16：`HostCapabilitiesTest` 9/9、
`PixelHostCapabilitiesIntegrationTest` 5/5、`MediaQueryHostCapabilitiesCompatibilityTest` 2/2。
retained/focus 相关回归另有 33/33。KDoc 为 1,103/2,034（54.23%，门禁 35%）；本工作包新增/
修改的 class、变量和方法均具备必要职责或兼容说明。

同一最终集成态还通过 secret/backup、Lint、Release AAR/POM/sources/Javadoc、外部 RenderObject
SPI、RouteEntry、旧二进制与 Kotlin/Java SDK consumer、六场景 perf、soak、`git diff --check`
和严格 MkDocs。性能报告为 `overallPass=true`。Release AAR 为 2,877,351 bytes，SHA-256：
`015111b2753fcf0903b27a6d582db659fc919826c91a6f616ccca6363b3a2c82`。

项目尚无正式发布过的 released Metalava signature，因此 released compatibility 按既有预发布
规则记录为 `SKIPPED/NO_RELEASED_BASELINE`；current public/binary/Metalava baseline、真实旧二进制
运行和隔离消费者均已通过。这不是 skipped test，正式签名冻结仍由 M9-3 收口。

## 后续边界

- M5-3B 在 localization fallback 行为冻结前补充 deprecated alias、grandfathered tag 和 extension
  的 JVM/API 37 compatibility corpus；若运行时 canonical 结果不同，必须采用 engine-owned 规则；
- M5-3E 负责从 Android Configuration、Accessibility、Display 和 window 信息自动构造 snapshot，
  并补 API 24/29/30/37 差异证据；
- M5-3F 再把 locale、RTL、textScale、contrast、resize、display feature 和 Unicode 全链路汇总到
  Demo、golden、用户手册与最终设备门禁。
