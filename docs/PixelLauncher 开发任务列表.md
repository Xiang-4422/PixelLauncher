# PixelLauncher 开发任务列表

日期：2026-06-20

本文从 `docs/PixelLauncher 后续迭代方向.md` 拆分而来，用作后续开发执行清单。任务列表只记录已确认方向，不引入新的产品范围。

## 使用规则

- 每个任务开始前，先确认是否符合 `docs/PixelLauncher 后续迭代方向.md` 和 `docs/PixelLauncher UI规范.md`。
- UI 任务必须避免固定尺寸、文字裁切、列宽压缩和 padding 不一致。
- Drawer 任务只能围绕 App 搜索、别名和重命名展开。
- SMS 搜索只能存在于 SMS 内部，不能进入 Drawer。
- root 替代系统锁屏不进入 PixelLauncher 主 App。

## 已落地基线

以下内容已经作为后续开发的基础能力存在，新增任务应建立在这些能力之上：

- Home 已有日期、天气、闹钟、未接电话、未读短信、使用时长、打开次数、终端状态和底部 `CALL / SMS` 入口。
- Home 状态行已有点击动作和长按临时消息。
- Drawer 已有 App 搜索、拼音搜索、别名、重命名、启动、排序和长按进入 App 管理。
- Settings 已有 Display / Home / Drawer / Idle / Data / Advanced 分组。
- Settings 已移除字体大小和 fontStyle 用户设置。
- Idle 已支持充电自动进入、无操作自动进入和默认 30s 超时配置。
- SMS 已有默认短信角色申请、线程列表、会话详情、未读收件箱、草稿输入、发送和首次 loading。
- 全局状态栏已作为临时消息位置使用。
- UI 间距基线已集中到 `LauncherSpacing`。

## P0：近期必须推进

### P0-01 Home 决策层与异常状态

- [x] 梳理 Home 状态优先级，确保通信、时间、环境、使用统计按确定规则排序。
- [x] 为天气、Usage、Call Log、SMS、Notification 等数据失败增加明确可见状态。
- [x] 后台刷新失败时显示短原因，避免 Home 出现空白或静默失败。
- [x] 保持 Home 常显信息克制，目标是打开后 1 秒内知道是否有事。

验收：

- [x] Home 不成为 Dashboard。
- [x] 数据缺权限、无数据、刷新失败都有可见解释。
- [x] 状态行仍然最多显示重要信息，不挤占天气常显行。

证据：

- `HomeInfoModelTest` 覆盖通信优先、通知摘要、闹钟、低电量、Usage、最多 3 行和天气常显行不占用优先级行。
- `TerminalStatusProviderTest.buildStatus_dataIssuesAppearBeforeQuietReadyStates` 覆盖 Data Health 异常在 Home 终端状态中显示为 `DATA n ISSUE`。
- `HomeInfoDetailModelTest` 覆盖天气刷新中、刷新失败、无定位、Usage 缺权限和刷新结果短文案。
- `DataHealthModelTest` 覆盖 Usage、Location、Call Log、SMS、Notification 的短原因。

### P0-02 Data Health 可解释性深化

- [x] 审核 Usage Access、Location、Call Log、SMS、Notification Listener 的异常展示是否覆盖全部真实失败路径。
- [x] 补齐缺失项的短原因，例如 `RUNTIME PERM`、`DEFAULT SMS ROLE`、`LISTENER ACCESS`。
- [x] 补齐缺失项的修复入口：请求权限、打开系统设置、申请默认短信角色或打开 Listener 设置。
- [x] 保持 Data Health 与 Advanced / Diagnostics 的摘要一致。

验收：

- [x] 用户能理解 Home 数据为空的原因。
- [x] 每个可修复异常都有明确入口。
- [x] 状态刷新后 UI 能及时反映最新结果。

证据：

- `DataHealthModelTest` 覆盖 Usage、Location、Call Log、SMS、Notification Post、Notification Listener 的值和短原因。
- `DataHealthRepairActionModelTest` 覆盖每个 Data Health 行对应的修复动作。
- `DataHealthModel` 同时驱动 Settings 摘要、Data Health 页面和 Diagnostics 文本采样，避免摘要来源分裂。
- `LauncherStateTransitionsTest.updateDataHealthPreservesOrWritesRefreshTime` 覆盖更新时间写入；`MainActivity.onDataHealthItemPressed` 在执行修复动作后刷新 Data Health 状态。

### P0-03 App 管理交互收敛

- [x] 将 App 重命名和别名编辑整理成稳定入口。
- [x] Drawer 长按和 Settings 都能进入 App 管理。
- [x] 设计像素风轻量浮层菜单，用于 Drawer 长按操作。
- [x] App 缓存刷新入口保持清晰，不进入 Drawer 搜索结果列表。

验收：

- [x] Drawer 列表只显示 App 标题。
- [x] 别名只影响搜索，不挤占列表行空间。
- [x] 重命名标题优先显示。

证据：

- Drawer 长按先打开轻量浮层菜单，菜单只提供 `EDIT`、`REFRESH`、`CANCEL`。
- `EDIT` 进入完整 App 管理页，复用重命名和别名编辑入口；Settings 的 `APP MANAGEMENT` 仍直接进入同一页面。
- `REFRESH` 只在浮层或 App 管理页触发缓存刷新，不进入 Drawer 搜索结果列表。
- `LauncherStateTransitionsTest` 覆盖 Drawer 浮层打开、关闭、进入 App 管理和离开 Drawer 时自动收起。
- `UiSpecStaticTest.drawerLongPressActionsStayInLightweightOverlay` 和 `drawerListDoesNotRenderSearchMatchReasonTags` 约束 Drawer 列表仍只显示 App 标题。

### P0-04 Idle 自动进入稳定性验收

- [x] 审核充电自动进入 Idle 的触发和退出行为。
- [x] 审核无操作默认 30s 自动进入 Idle 的触发和退出行为。
- [x] 审核 Settings 配置变更后是否立即影响 Idle 触发规则。
- [x] 稳定 Home / Drawer / Settings / SMS 与 Idle 自动进入之间的页面切换关系。

验收：

- [x] 充电和无操作能按设置进入 Idle。
- [x] 用户操作后不会误触发 Idle。
- [x] 不引入系统锁屏替代职责。

证据：

- `IdleAutoEntryPolicy` 集中定义自动进入 Idle 的页面、充电、无操作和 launch-pending 规则。
- `IdleAutoEntryPolicyTest` 覆盖充电触发、当前充电配置即时生效、无操作倒计时、超时归零、关闭配置和非 Home / Drawer 页面阻断。
- `LauncherStateTransitionsTest` 覆盖进入 Idle 时的 return mode 和非 Home / Drawer 页面阻断。

### P0-05 UI 规范持续落地

- [x] Launcher 新增页面统一使用 `LauncherSpacing`。
- [x] Home、Drawer、Settings、状态栏和底部动作区的边距规则保持一致。
- [x] Settings 不暴露字体大小和 fontStyle 用户选项。
- [x] 字体尺寸只能由 UI 通过受控 enum 选择。
- [ ] 每个新增页面真机截图检查文字裁切、边框贴字和左右栏压缩。

验收：

- [x] 不出现文本被裁切。
- [x] 边框按钮内部 padding 稳定。
- [x] 两栏布局左栏按内容占用，右栏使用剩余空间。

证据：

- `UiSpecStaticTest.screenSourcesUseLauncherSpacingForPageRhythm` 禁止 screen 直接写页面级 `2px` padding 和普通 `2px` 行距。
- `UiSpecStaticTest.sharedLauncherSpacingTokensDriveTopLevelPagesAndControls` 覆盖 Home、Drawer、Settings、状态栏、Home 底部动作区和 Settings Switch。
- `UiSpecStaticTest.screenAndWidgetSourcesAvoidKnownTextClippingPatterns` 禁止已知裁切模式和不受控 font scale。
- `PixelFontCatalogTest` 覆盖 `PixelFontSize` enum、默认 UI 字号和字体 metrics；Settings 只保留 UI 外观项，不暴露字体大小和 fontStyle。

### P0-06 Drawer 搜索质量验收

- [x] 为别名、重命名、拼音全拼、拼音首字母、英文标签、包名派生命中补齐测试。
- [x] 保留搜索命中解释在模型、排序调试和测试中。
- [x] 确认 Drawer 列表行不显示 `PINYIN`、`ALIAS`、`PACKAGE` 等命中标签。
- [x] 审核重命名和别名对排序结果的影响。

验收：

- [x] Drawer 没有额外分类层级。
- [x] 搜索仍然只返回 App。
- [x] 搜索结果更符合用户自己的命名习惯。
- [x] 搜索结果列表只显示 App 标题。

证据：

- `DrawerQueryTransitionsTest` 覆盖别名、重命名、拼音全拼、拼音首字母、英文标签、包名尾部、包名全文、Activity 名和排序影响。
- `DrawerSearchSupportTest` 保留 `ALIAS`、`PINYIN`、`PKG`、`ACT` 命中来源解释。
- `UiSpecStaticTest.drawerListDoesNotRenderSearchMatchReasonTags` 保证 Drawer UI 列表只显示 App 标题。

## P1：差异化能力

### P1-01 Notification Listener 摘要配置

- [x] Settings 中增加 App 级静音配置。
- [x] Settings 中增加显式优先来源配置。
- [x] 摘要规则保持：静音过滤、ongoing 过滤、高优先级或显式优先来源、最多 2 个来源、剩余 `+N`。
- [x] Home / Idle 只显示一行 `NOTIFY ...`，不展示完整通知流。
- [x] 点击通知摘要进入系统通知面板或对应 App。

验收：

- [x] 通知摘要不变成完整通知中心。
- [x] 用户能关闭不想看的来源。
- [x] 摘要内容可解释、可关闭。

证据：

- `NotificationSettingsScreen` 只列来源与 `NORMAL / PRIORITY / MUTED` 状态，点击来源循环配置，不展示完整通知流。
- `NotificationSummarySettingsRepository` 持久化静音来源与显式优先来源；`NotificationSummaryStore.updateRules` 在配置变化后立即重算摘要。
- `NotificationSummaryModelTest` 覆盖静音过滤、ongoing 过滤、高优先级、显式优先来源、最多 2 个来源和 `+N`。
- `HomeInfoModelTest` 与 `IdleStatusModelTest` 覆盖 Home / Idle 只显示一行 `NOTIFY ...`；Home 点击通知摘要时单一来源打开对应 App，否则进入系统通知设置。

### P1-02 数字健康展示

- [x] 自研今日使用时长统计。
- [x] 自研今日打开次数统计。
- [x] Home 显示简短摘要。
- [x] Settings / Diagnostics 展示数据来源和权限状态。

验收：

- [x] 只做展示，不做冷却、拦截或夜间隐藏 App。
- [x] 数据异常时有可见原因。
- [x] Drawer 启动路径不增加阻断。

证据：

- `ScreenUsageSummaryModelTest` 覆盖今日亮屏使用时长和打开次数的自研聚合逻辑；`ScreenUsageRepository` 只负责读取 Android UsageEvents 并交给纯模型计算。
- `HomeInfoModelTest` 覆盖 Home 中短摘要 `USE xx:xx  OPEN n`；`HomeInfoDetailModelTest` 覆盖 Usage 缺权限与刷新结果文案。
- `DataHealthModelTest` 与 `DataHealthRepairActionModelTest` 覆盖 Usage Access 缺失原因和系统设置修复入口。
- `DiagnosticsModelTest` 覆盖 `USAGE EVENTS / NO ACCESS` 数据来源与权限状态；Drawer 启动路径只记录 `LauncherStatsRepository` 打开次数，不增加阻断或限制。

### P1-03 SMS 完整短信能力

- [ ] 联系人名称映射。
- [ ] Home 未读短信摘要直达对应短信视图。
- [ ] 验证码识别和快速复制。
- [ ] 正文选择、复制、重发。
- [ ] 发送中、发送失败、已发送状态。
- [ ] 线程内搜索：联系人名、号码、正文。
- [ ] 长短信分页或稳定换行。
- [ ] 空状态、loading、错误状态保持一致。
- [ ] 权限缺失和默认短信角色缺失可见。

验收：

- [ ] 首次进入、空线程、长文本、发送失败都不是空白或裁切状态。
- [ ] SMS 搜索不进入 Drawer。
- [ ] Home 的 SMS 摘要能直达对应短信视图。

### P1-04 Idle 视觉状态系统

- [x] 默认态。
- [x] 充电态。
- [x] 低电量态。
- [x] 天气风险态。
- [x] 通知摘要态。
- [x] 夜间降低亮度。
- [x] 充电常显时降低信息密度。

验收：

- [x] Idle 是 Launcher 内部稳定页面。
- [x] Android 系统锁屏仍完全负责安全锁屏。
- [x] Idle 不实现 PIN、密码、生物识别或 root 锁屏替代。

证据：

- `IdleStatusModelTest` 覆盖默认态、充电态、低电量态、天气风险态和通知摘要态的优先级与文案。
- `IdlePresentationModelTest` 覆盖夜间降低亮度时间窗，以及充电态隐藏 footer 以降低常显信息密度。
- `IdleScreen` 只消费 `IdleStatusModel` 和 `IdlePresentationModel`，未加入 PIN、密码、生物识别或 root 锁屏替代逻辑。

### P1-05 Advanced / Diagnostics 深化

- [x] 增加真实渲染 bounds 检查。
- [x] 暴露关键文案宽度采样结果。
- [x] 增加可操作的调试入口。
- [x] Data Health、Home 摘要、字体 metrics、display、status bar inset 和电量状态保持一致展示。

验收：

- [x] UI 裁切问题能通过诊断页定位。
- [x] 调试入口不影响普通用户路径。
- [x] 诊断信息短、明确、可复现。

证据：

- `DiagnosticsBoundsModelTest` 覆盖 Advanced 可用内容宽度、正文高度、可见行数和 bounds 风险摘要。
- `DiagnosticsTextSampleModelTest` 与 `DiagnosticsModelTest` 覆盖 `TEXT`、`TEXT MAX`、`TEXT RISK` 行，能定位关键文案宽度风险。
- `DiagnosticsModelTest` 覆盖 Home 摘要、Data Health 摘要、字体 metrics、display、status bar inset、电量状态、`BOUNDS` 和 `DEBUG DATA HEALTH` 行。
- `UiSpecStaticTest.diagnosticsKeepsDataHealthDebugAction` 保证 Advanced 页的 `DEBUG DATA HEALTH` 行可点击进入 Data Health。

## P2：长期探索方向

### P2-01 官方状态块

- [ ] 日程状态块。
- [ ] 媒体状态块。
- [ ] Todo 状态块。
- [ ] 倒计时状态块。
- [ ] 同屏最多一个主状态块。

验收：

- [ ] 不演变成自由 widget 大屏。
- [ ] 状态块服务 Home 的重要信息判断。
- [ ] 每个状态块都能关闭或降级。

## 明确不做

- 不做传统图标墙。
- 不做自由 widget 大屏。
- 不做完整通知中心。
- 不做默认联网搜索。
- 不在 Launcher 中处理系统锁屏替代。
- 不引入复杂主题市场。
- 不做强干预数字健康。
- Drawer 不搜索联系人、短信线程、设置项、网页、计算器或命令。

## 开发前待确认

- [ ] Home 重要信息优先级的最终排序细节。
- [ ] Notification Listener 的 App 级静音和优先来源配置 UI。
- [ ] SMS 联系人映射优先使用系统联系人还是先做号码归一化。
- [ ] UI 规范落地后，pixel-demo 是否同步作为规范验收宿主。
