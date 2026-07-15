# M5-3B Localization provider、标准组件迁移与 API 37 验收

## 结论

2026-07-13，M5-3B 已完成。SDK 新增显式 opt-in、平台中立的 Localization 模型，标准组件
可以按有序 Host locale、应用 delegate 和确定性 fallback 解析默认文案，同时保留未安装 provider
时的既有 theme label、scope-less legacy 像素、element tree 和旧二进制行为。

本工作包完成了 25 个生产组件、NavigationBar/Rail 的 direct/controller 四个入口、scope-less
legacy facade、中英内置 bundle、自定义 bundle、整数/百分比 formatter、嵌套 provider、blank 与
显式覆盖契约。locale/provider 热更新不会替换 TextField controller/State、文本、selection、
composition、焦点、Navigation 选择、Android virtual ID、className 或固定尺寸节点 bounds。

M5-3B 范围内没有 P0/P1 代码、测试、文档或兼容遗留。Unicode grapheme 编辑、code-point 字体、
Bidi 段落与 Android capability 自动采集分别属于 M5-3C、M5-3D 和 M5-3E，本报告不以当前
Localization 能力冒充这些后续工作。

## 公开模型与解析不变量

`PixelLocalizations.kt` 新增七个公开类型：

- `PixelIntegerFormatter`、`PixelPercentFormatter`：不暴露平台 locale 类型的 SAM formatter；
- `PixelLocalizationBundle`：一个 exact locale 的 29 个标准 label、NavigationBar/Rail 名称和
  两类 formatter；
- `PixelLocalizationDelegate`：只负责 exact lookup；
- `PixelLocalizationResolver`：负责完整 fallback 和文案优先级；
- `PixelLocalizationProvider`：读取 ordered Host locales、解析 bundle 并显式安装 inherited scope；
- `PixelLocalizations`：直接安装已解析 bundle，并向最近的 descendant 提供 locale/bundle。

Host 不会自动安装 Localization。`PixelLocalizationProvider.localeOverride == null` 时才读取完整
`HostCapabilitiesData.locales`；非空 override 不建立 Host locale 依赖。对每个 requested locale，
resolver 依次尝试 exact canonical tag 和 language-only tag，然后继续下一个 preference，再尝试
configured default 的 exact/language，最后以 English 终结。每个候选都先查 consumer delegate，
再查 built-in delegate。

Provider 发布的 active locale 保留第一个请求值，即使资源 bundle 来自后续 preference、configured
default 或 English。delegate 为一个候选返回不同 `bundle.locale` 会立即失败，避免隐藏错误映射。
`PixelLocale` 的 canonical 结果没有依赖 SDK 自行猜测：行为冻结前已建立 JVM/API 37 一致性 corpus。

组件可选文案的固定优先级为：

```text
非 sentinel 的组件显式文案 → 最近的 PixelLocalizations → PixelThemeTokens.labels → English
```

`PixelLocalizationResolver.resolveText` 拒绝 present blank。少数历史 overload 在进入严格 resolver
前保留显式 blank，冻结的 English 默认参数继续作为“省略” sentinel；这两类兼容点均有定向测试和
迁移文档。Radio、IconButton、destination、Menu item、Tabs/Segment 选项和消息正文等必填业务
内容仍由调用方拥有，不会被 SDK 自动翻译。

## `PixelLabelTokens` ABI 与旧消费者

本次没有向 `PixelLabelTokens` 的 29 字段主构造器增加属性。其 constructor、no-arg/default
constructor、`copy`、`copy$default` 和 `component1..29` 的 JVM descriptor 已由专项兼容测试锁定。
NavigationBar/Rail 名称和 formatter 作为 additive bundle 能力提供。

三份 reviewed API baseline 相对 M5-3A 交接态均为纯新增、零删除：

| baseline | 新增 | 删除 | SHA-256 |
|---|---:|---:|---|
| public API | 47 | 0 | `56f92c1705c722ebac47bd7c4e6a3e82b68748eb71fcf1be36975bbe253c32ee` |
| JVM binary API | 98 | 0 | `3a8d8e0edca53df0283fcfcedfe162e027f4eb071c8f09a4dd5441dcb0a33533` |
| Metalava API | 121 | 0 | `6245fb2190b482341ef43266d4bdba553040e0146cb76cea92d498a1d6d51269` |

新增签名只包含上述七个 Localization 类型，没有 `android.*`、`androidx.*` 或 `java.util.Locale`
泄漏。`checkPublicApi`、`checkBinaryApi`、`checkMetalavaApi`、`checkStableApiBoundary` 和冻结旧 AAR
consumer 均通过。冻结旧 AAR 的 SHA-256 为
`a88dd712...8bfca`，旧 consumer 在当前 runtime 成功构建并运行。

项目仍没有正式发布过的 released Metalava signature，因此 released compatibility 按预发布契约
记录为 `SKIPPED/NO_RELEASED_BASELINE`。current 三份 baseline、真实旧二进制运行和隔离 consumer
均已验证；这里的状态不是 skipped test，正式 1.0 signature 由 M9-3 冻结。

## 25 个组件与真实语义矩阵

`ProductionComponentLocalizationAcceptanceTest` 使用与
`ProductionComponentStateMatrixTest` 完全一致且顺序固定的 25-family registry。验收覆盖：

- 25 × English/Chinese/RTL-custom 共 75 个真实生产组件单元；RTL case 同时安装真实
  `Directionality(RTL)`；
- 20 类同时验证独立 Loading/Error，Progress 验证 Error-only，四类验证没有伪造 generic status；
- 扫描真实 semantics 的 label/value/hint/error/custom action，Chinese/RTL 下没有内置 English
  fallback 或 theme sentinel 泄漏；
- Radio、IconButton、Navigation destination、Menu item、Tabs/Segment、Toast/Snackbar/Tooltip
  业务文案在三种 provider 下保持不变；
- Toast/Snackbar/Tooltip 的 3 × 3 blank message fallback；
- NavigationBar/Rail direct/controller 四入口 × 三 provider；
- 19 类 non-sentinel custom explicit override；
- Slider/Progress 百分比和 ValueAdjuster 整数输出消费 bundle formatter。

Acceptance 5/5 与相邻状态矩阵 3/3 共 8/8 通过；相邻矩阵仍覆盖 25 × 8 = 200 个状态单元和
每族独立颜色通道。组件级 focused localization suites 还覆盖：核心 resolver/provider 9/9、Java
interop 1/1、legacy fallback 2/2、Input/Scroll 4/4、Navigation 4/4、Overlay/Slidable 4/4、
PixelComponents 6/6。

scope-less legacy Button、TextButton、Checkbox、Switch、Progress、Dialog、Tooltip 等在未安装
provider 时保持历史像素和 semantics；安装 provider 只改变默认文案，不会让
`LegacyFacadeThemeSwitch` 进入 theme 视觉分支。一次全量测试曾发现新 wrapper 改变两个 element
snapshot，最终通过复用既有 compatibility switch 的 `BuildContext` 恢复历史 direct child，未修改
任何 snapshot 来接受回归。

## Locale compatibility corpus

JVM 与 API 37 使用相同的 38-case corpus，覆盖三种 deprecated alias、全部 26 个 grandfathered
tag、大小写 canonical、Unicode extension 和 private-use。两端 canonical tag 逐项完全一致，因此
本工作包不需要引入第二套 engine-owned canonicalizer。

resolver 测试另覆盖 ordered preference、exact→language、configured default、English terminal、
custom delegate 优先、duplicate canonical locale 拒绝、incoherent delegate 拒绝、nested provider、
Host locale 更新与 explicit override 不订阅 Host 的差异行为。

## API 37 状态与 Accessibility 身份

设备为 `emulator-5554`、API 37 / Android 17、`sdk_gphone16k_arm64`，fingerprint 为
`google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys`。

真实 connected instrumentation 2/2 通过：

- `PixelLocaleCompatibilityInstrumentedTest` 复验上述 38-case canonical corpus；
- `PixelLocalizationAccessibilityRetentionInstrumentedTest` 在 attached `PixelHostView` 内执行
  English → Chinese → custom provider 更新，并通过真实 Android `AccessibilityNodeProvider` 读取
  节点。

热更新前后，TextField controller/State 引用保持，文本为 `pixel engine`、selection `2..7`、
composition `1..5`、输入焦点和 semantic ID 保持；Navigation collection 与 Home destination 的
virtual ID、className、bounds 保持，Home selected/accessibility focus 和 controller 选择保持。只有
预期 label/status 发生变化。

## 独立消费者、文档与门禁

`tools/pixel-sdk-consumer-smoke.sh` 从临时 file-Maven 仓库解析真实 POM/AAR，而不是 project
dependency：

- Kotlin consumer 构造 custom `fr-CA` bundle、delegate/provider、blank Button 和 Slider，并验证
  custom label/percent formatter；
- Java consumer 使用 Java SAM formatter、map delegate、resolver 和静态 `resolveText`；
- consumer JVM 单测、独立 Android APK 编译和组装均通过。

迁移指南 `docs/migrations/1.0.0-localization.md` 记录 opt-in Host 边界、fallback、优先级、29 字段
ABI、自定义/嵌套 provider、Kotlin/Java、sentinel、blank、scope-less facade、mandatory business
label、调试与明确排除项。Java 示例已对当前 classes.jar 实际执行 `javac`；严格 MkDocs 通过。

首次完整 release gate 发现 theme-token scanner 不认识 `PixelLabelTokens::field` 属性引用和
`?.labels?.field` Kotlin safe-call，产生 21 个假阴性。最终修复 scanner 并增加两类语法回归，工具
专项 14/14、完整 Python tooling 35/35、25/25 component token 和 10/10 foundation group 通过；
没有用 allowlist 或降低阈值绕过门禁。

最终 `tools/pixel-release-check.sh` 成功，包含：

| 范围 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` 完整 JVM | 1169 | 0 | 0 | 0 |
| `pixel-demo` JVM | 26 | 0 | 0 | 0 |
| Launcher app JVM | 328 | 0 | 0 | 0 |
| Python tooling | 35 | 0 | 0 | 0 |
| API 37 Localization | 2 | 0 | 0 | 0 |
| lifecycle/resource soak | 9 | 0 | 0 | 0 |

同一最终集成态还通过 secret/backup、Lint、Release AAR/POM/sources/Javadoc、external RenderObject
SPI、RouteEntry、旧二进制、SDK consumer、六场景 perf、soak、`git diff --check` 和严格 MkDocs。
性能报告 `overallPass=true`。KDoc 为 1,124/2,080（54.04%，当前门禁 35%），本工作包新增/修改的
类、变量和方法均具备职责、不变量或兼容边界说明。

Release AAR 为 2,949,633 bytes，SHA-256：
`ba92e74377369c49f64b3a0633974addb9fa9c7ec9d39d18766dacfca2727ec3`。

## 后续边界

- M5-3C 建立固定 Unicode 版本的 grapheme boundary kernel，并统一 Controller、IME、Tester 与
  Accessibility 的 UTF-16 offset 规范化和编辑边界；
- M5-3D 把 glyph、paragraph、caret、hit test 与 selection 几何迁移到 code point/grapheme/Bidi
  共用模型；
- M5-3E 负责 Android locale/textScale/contrast/density/inset/display-feature 自动采集和 adaptive
  viewport；
- M5-3F 汇总 Demo、LTR/RTL/textScale/high-contrast/small-window golden、API 24/29/37 与最终文档
  门禁。
