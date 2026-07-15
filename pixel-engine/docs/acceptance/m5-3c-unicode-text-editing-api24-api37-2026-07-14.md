# M5-3C Unicode boundary、文本编辑与 API 24/37 验收

## 结论

2026-07-14，M5-3C 已完成。SDK 使用 engine-owned、平台中立的 Unicode 17.0.0 extended
grapheme boundary map 统一 Controller、saved state、composition、剪贴板、选词、方向移动、
前后删除、点击/拖动 selection、`PixelTester`、Accessibility 与 Android `InputConnection`。
selection、composition 和 Accessibility 继续使用 Android 兼容的 UTF-16 offset，但所有稳定
端点都落在 grapheme boundary；文本不会被隐式 NFC/NFD 或大小写改写。

本工作包还补充了不会截断 supplementary code point 的 exact text input 路径，以及默认的
engine-owned 隐藏编辑器和 generation-aware `InputConnection`。API 24 与 API 37 在同一最终集成
态分别通过 12/12 个真实 instrumentation 用例；完整 JVM、API/ABI、Lint、Release AAR、外部
consumer、性能、soak 和严格文档门禁通过。M5-3C 范围内没有 P0/P1 代码、测试、文档或兼容
遗留。

Code-point glyph lookup、cluster paragraph、ellipsis、Bidi 视觉重排、caret/hit-test/selection 几何
仍属于 M5-3D。本报告不以 grapheme-safe 编辑冒充字体覆盖、复杂 shaping 或 Bidi 布局能力。

## Unicode 权威与索引模型

1.0 文本索引契约固定为：

| 层级 | 稳定契约 |
|---|---|
| selection / composition / Accessibility | UTF-16 offset |
| 字符解码和后续 glyph pack key | Unicode code point `Int` |
| 光标、删除、选择和用户可见编辑 | Unicode 17.0.0 extended grapheme cluster |
| 算法版本 | UAX #29 revision 47 |

`PixelGraphemeBoundaryMap` 由生成后的纯 Kotlin Unicode property tables 驱动，不调用设备 ICU，
因此不会随 Android API 版本改变。公开能力包括 `previous`、`next`、`floor`、`ceil`、`nearest`、
`expand` 和 `isBoundary`：

- 非空范围按 `floor(start)` / `ceil(end)` 向外扩展；
- 折叠 caret 使用 `nearest`，等距时选择更大的 UTF-16 offset；
- `previous` / `next` 严格跨越一个完整 cluster；
- 所有输入 offset 先 clamp 到 `0..text.length`；
- CRLF、Control、Extend、SpacingMark、Prepend、Hangul、variation selector、emoji modifier、
  ZWJ extended-pictographic、regional-indicator 奇偶、keycap 和 GB9c 均由固定规则覆盖。

官方 `GraphemeBreakTest-17.0.0.txt` 的完整 766-case corpus 逐项通过，运行时不会写入或自动接受
失败基线。输入文件和许可证均固定摘要：

| 文件 | SHA-256 |
|---|---|
| `GraphemeBreakTest.txt` | `e2d134d2c52919bace503ebb6a551c1855fe1a1faec18478c78fff254a1793ec` |
| `LICENSE-UNICODE.txt` | `e7a93b009565cfce55919a381437ac4db883e9da2126fa28b91d12732bc53d96` |

Unicode License v3 既保存在测试 corpus 旁，也进入 Release AAR 内嵌的
`classes.jar!/META-INF/LICENSE-UNICODE.txt`；`UnicodeGraphemeData.class` 与许可证均已从最终 AAR
实际列出确认。

## 文本编辑不变量

`PixelTextFieldController` 和所有公开输入路径共享同一 boundary map。以下行为均有定向测试：

- `setSelection`、composition update/clear、saved-state restore 与倒序范围；
- replace、paste、copy、cut、backward/forward delete；
- caret 前后移动和连续 Shift 扩选锚点；
- `selectWordAt`、tap caret、drag selection handle；
- `PixelTester` compose/update/edit/`pressText`；
- Accessibility `ACTION_SET_SELECTION`、set-text 与真实 semantic offset。

`e\u0301`、supplementary emoji、肤色序列、family ZWJ、variation selector、keycap、RI 国旗、
CRLF 和 legacy 非配对 surrogate 均只有确定的编辑边界。已有状态中的非配对 surrogate 作为一个
隔离恢复单元原样保留，避免升级时静默改写；新的 IME commit/composition 拒绝引入非配对
surrogate。`deleteSurroundingTextInCodePoints` 在遍历到 malformed surrogate 时 no-op，不删除
半个未知字符。

固定 seed `5783548151648587` 的编辑 fuzz 执行 12,000 步，覆盖 selection、composition、replace、
方向移动、UTF-16/code-point 删除、paste、cut 和 select-word。每一步同时校验：原始文本精确
相等、selection/composition 边界合法、既有 well-formed 输入不产生孤立 surrogate，以及失败时
输出 seed、step、operation 和最近 64 步可复现 trace。该工作量高于 Goal 的 10,000 次门槛。

## Android engine-owned InputConnection

默认 `PixelTextInputBridge` 创建 SDK 拥有的隐藏编辑器，并包装真实平台
`BaseInputConnection`/`InputConnection`。生产路径保证：

- selection-only 和 composition-only 变化同步回 retained TextField；
- commit/composing、UTF-16/code-point surrounding delete、set-selection、硬件方向/删除键及
  API 34 attributed replace 都经过 grapheme guard；
- begin/end batch 只在平台实际接受 nesting 时合并 callback；
- target 切换、restart、blur、close 和 Host rebind 使用 generation 淘汰旧连接；
- TextField A → B → A 切换时，各自文本、selection、composition 和连接完全隔离；
- 旧连接不能修改新的目标，真实 Host 更新也不会被尚未结束的平台 batch 静默覆盖。

显式传入普通自定义 `EditText` 仍作为弱兼容路径保留，但 SDK 无法包装任意子类的
`InputConnection`，因此不承诺完整 selection-only/composition-only 观察。需要完整 1.0 契约的
消费者应使用默认编辑器；自定义 Host 需要完整 composition 同步时实现 additive
`PixelTextEditingHostBridge`。旧 `PixelHostBridge` 的冻结描述符与实现无需新增抽象方法。

## 补充平面与 exact text input

旧 `PixelKeyEvent.character: Char?` 保留给单个非 surrogate BMP 字符。新增
`PixelTextInputEvent(text)`、`Focus(onTextInput = ...)`、
`PixelHostView.dispatchPixelTextInput` 和 `PixelTester.pressText`，以一个 `String` payload 传递
supplementary code point、完整 grapheme 或多 code-point commit。未消费 payload 只有在恰好包含
一个非 surrogate BMP 字符时才回退到旧 `PixelKey.CHARACTER`，不会把 supplementary 字符拆成
两个 surrogate key event。

## API 24 与 API 37 设备证据

最终命令在两个设备上分别运行同一组生产 InputConnection 和 ICU differential 用例：

```bash
ANDROID_SERIAL=<serial> ./gradlew \
  :pixel-engine:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.purride.pixelui.PixelGraphemeInputConnectionInstrumentedTest,com.purride.pixelui.PixelGraphemeIcuDifferentialInstrumentedTest'
```

| 设备 | 平台 Unicode/ICU | tests | failures | errors | skipped |
|---|---|---:|---:|---:|---:|
| API 24，Android 7.0，`google/sdk_google_phone_arm64/generic_arm64:7.0/NYC/8695085:userdebug/dev-keys` | Unicode 8.0.0 / ICU 56.1 | 12 | 0 | 0 | 0 |
| API 37，Android 17，`google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys` | Unicode 17.0.0 / ICU 78.3 | 12 | 0 | 0 | 0 |

API 24 的 differential corpus 为 10 个 controlled case 加 24 个官方 reviewed subset case：34 项中
21 项与旧 ICU 直接一致，13 项逐项命中版本化、人工审阅的 expected-difference 表，`problems=0`。
差异包括旧 ICU 对 emoji modifier、GB11、GB9c、Prepend 和 RI pairing 的历史行为，不会反向改变
引擎固定的 Unicode 17 结果。

API 37 的同一 34-case corpus 与引擎结果 34/34 直接一致，`reviewedDifferences=0`、`problems=0`。
两端完整原始 UTP、XML、device-info 和 logcat 报告分别保存在：

- `pixel-engine/build/reports/device/m5-3c/api24/`
- `pixel-engine/build/reports/device/m5-3c/api37/`

## API、ABI 与兼容审阅

三份 current baseline 相对 M5-3B 交接态的差异已经逐项审阅：

| baseline | 新增 | 表面删除 | 当前 SHA-256 |
|---|---:|---:|---|
| public API | 42 | 3 | `e7369709d47b7c29b2f4d163adb5c2c01a5ad9af91dad91e5d328052b33aae4a` |
| JVM binary API | 87 | 1 | `7e01ad5f3e128317d810b7228925c3d91bcb44d73ee8569017b9e12b6c067c82` |
| Metalava API | 86 | 1 | `34fd21d9f1f6cd7827947381306773ab162c7edca2c661bd459659c8d4bd0537` |

新增面向消费者的能力为 `PixelGraphemeBoundaryMap`、`PixelUtf16Range`、完整编辑值/Host bridge、
exact text event、Controller grapheme-safe 操作和 Tester `pressText`。public dump 的三行表面删除是
默认表达式/实现表示变化；binary/Metalava 的一行表面删除是
`PixelHostBridge` → additive `PixelTextEditingHostBridge` 父类型表示变化，旧方法和 JVM 描述符没有
删除。外部旧二进制、Kotlin/Java SDK consumer、RenderObject SPI 与 RouteEntry consumer 均真实
加载/构建通过。

`checkPublicApi`、`checkBinaryApi`、`checkMetalavaApi` 和 `checkStableApiBoundary` 通过；stable
boundary finding 为 0。项目尚未存在正式发布的 released Metalava signature，因此 released
compatibility 继续按预发布契约记录 `SKIPPED/NO_RELEASED_BASELINE`。这不是 skipped test；1.0
released signature 由 M9-3 冻结。

## 文档、产物与最终门禁

用户手册、架构说明、README、CHANGELOG 与
`docs/migrations/1.0.0-unicode-text-editing.md` 已同步 UTF-16/code-point/grapheme 三层索引、
normalization、malformed surrogate、默认/自定义输入路径、exact text 和 M5-3D 排除项。本工作包
新增/修改的类、变量和方法已补充职责、不变量或兼容边界说明。

最终 `tools/pixel-release-check.sh` 成功，包含 worktree/artifact secret scan、backup contract、
三份 API/ABI、stable boundary、25/25 component token、10/10 foundation token、KDoc、Python
tooling、engine/Demo/App JVM、Lint、Release AAR、sources/Javadoc、外部 SPI、RouteEntry、旧二进制、
隔离 Maven consumer、六场景 perf、9 项 lifecycle/resource soak 和严格 MkDocs。

| 范围 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` 完整 JVM | 1215 | 0 | 0 | 0 |
| `pixel-demo` JVM | 26 | 0 | 0 | 0 |
| Launcher app JVM | 328 | 0 | 0 | 0 |
| API 24 Unicode/IME | 12 | 0 | 0 | 0 |
| API 37 Unicode/IME | 12 | 0 | 0 | 0 |
| lifecycle/resource soak | 9 | 0 | 0 | 0 |

KDoc 为 1,166/2,119（55.03%，当前阶段门槛 35%）；全仓 public/protected 100% 仍由 M9-1
收口。性能报告的 list scroll、text input、animation、graphics primitives、page transition 和
overlay 六个场景均通过，`overallPass=true`。

最终 Release AAR 为 3,045,249 bytes，SHA-256：
`0b432235ae6a277c53cdc18dd89b84d702c60251082baed13aab62536f2b62cc`。

## 后续边界

- M5-3D 把 glyph/provider/cache 迁移到 code point，并建立 cluster paragraph、Unicode Bidi、caret、
  hit test、selection rect 和 Accessibility bounds 的共用几何；
- M5-3E 负责 Android capability 自动采集、viewport、density/textScale/inset/display feature 与
  自适应布局；
- M5-3F 汇总 Adaptive & Localization Demo、LTR/RTL/textScale/high-contrast/small-window golden、
  差异设备矩阵与 M5-3 最终集成门禁；
- M8 最终收口 API 24/29/36 全设备兼容矩阵，M9-1/M9-3 分别收口全仓 KDoc 100% 和正式 released
  signature。
