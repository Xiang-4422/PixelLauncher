# ADR-0001：Launcher 状态边界与增量拆分顺序

- 状态：提议（待实施任务逐阶段采纳）
- 基线提交：`e563c83d3b82ed5ba8a95cd89062af87fc22eda8`
- 盘点日期：2026-07-30
- 范围：`:app` 的 `LauncherState`、`LauncherUiState`、状态映射、状态转换和 `MainActivity` 编排
- 非目标：本 ADR 不实施生产代码重构，不处理发布、渠道或对外 API

## 1. 决策摘要

当前 `LauncherState` 不是约 130 个字段，而是 **108 个主构造参数**；`LauncherUiState` 同样是 108 个主构造参数，`toLauncherUiState()` 也恰好有 108 条同名赋值。计数口径只包含两个 data class 主构造器中以 `val` 声明的属性，不包含 enum、计算属性、Activity 字段或其他模型字段。

目标状态不按页面数量机械切分，而按写入来源、生命周期和行为内聚性划分为九个切片：

| 目标切片 | 字段数 | 所有权 |
|---|---:|---|
| `ShellState` | 6 | 顶层目的地、返回目的地和全局状态栏瞬态 |
| `AppCatalogState` | 17 | 应用目录、抽屉、应用编辑和应用启动统计 |
| `SettingsState` | 11 | 设置列表游标、外观、字体和抽屉偏好 |
| `SmsState` | 25 | 短信列表、会话、草稿、菜单、角色和短信能力 |
| `PhoneState` | 13 | 通话、拨号、联系人和联系人编辑 |
| `EffectState` | 10 | Idle、Pixel Matter 配置及最近交互时刻 |
| `HomeState` | 11 | 时间、Home 基础信息源、天气和用量摘要 |
| `NotificationState` | 6 | 通知实时摘要、通知项、来源目录和来源规则 |
| `SystemState` | 9 | 电池/充电以及跨业务共享的平台能力快照 |
| **合计** | **108** | 与当前 `LauncherState` 扁平字段一一对应 |

`SystemState` 是有意保留的共享只读快照，不强行归入 Home、Phone 或 Effect。`batteryLevel` / `isCharging` 同时服务 Home、全局 Header、Idle 和 diagnostics；权限字段同时服务 Data Health、Home 详情、Phone/SMS 入口。复制这些值到多个业务切片会立即产生一致性问题。目标规则是平台协调器写 `SystemState`，业务域只能读取。

`NotificationState` 也必须独立于 Home。四个实时摘要字段由 `NotificationSummaryStore` / listener 回调产生，两个来源规则字段由 `NotificationSummarySettingsRepository` 恢复和修改；规则变化又会通过 `NotificationSummaryStore.updateRules()` 重新计算并发布摘要。`app/src/main/kotlin/com/purride/pixellauncherv2/data/NotificationSummaryRepository.kt:28-98` 明确把 signals、rules、summary 和 listener 放在同一个 store，`app/src/test/java/com/purride/pixellauncherv2/data/NotificationSummaryRepositoryTest.kt:44-72` 也验证改规则会用当前 signals 立即重建摘要。它们共同表达“通知信号 + 规则 → 可见摘要”的单一业务聚合，同时被 Home、Settings、Notification Settings 和 Idle 消费。把它们归入 Home 会让 Home 获得通知设置写权限，也无法解释独立的持久化和监听生命周期；拆成 Notification 并不复制状态，反而给两类输入一个共同所有者。

最终应只保留一个规范化状态源。当前 `LauncherUiState` 没有裁剪、格式化或脱敏，纯粹复制 108 个字段，因此它不是有效的架构边界。完成切片迁移后，默认删除这一身份映射，让 Host/Screen 接收只读聚合状态或所需切片；只有出现可说明、可测试的 UI 专用派生值时，才保留一个更窄的 render projection。

## 2. 可复核基线

### 2.1 源码锚点

- `app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherState.kt:12-142`：108 个状态字段。
- `app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherUiState.kt:31-183`：108 个 UI 状态字段。
- `app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherUiStateMapper.kt:5-114`：108 条同名映射。
- `app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherViewModel.kt:20-32`：`MutableStateFlow<LauncherState>` 是当前进程内单一真值。
- `app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherStateTransitions.kt`：1,657 行、100 个公开转换方法和 14 个私有 helper。
- `app/src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt`：3,009 行。

### 2.2 计数命令

以下命令在基线提交上分别输出 `108`、`108`、`108`：

```bash
sed -n '/^data class LauncherState(/,/^)/p' app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherState.kt \
  | grep -Ec '^[[:space:]]+val[[:space:]]+[A-Za-z0-9_]+'
sed -n '/^data class LauncherUiState(/,/^)/p' app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherUiState.kt \
  | grep -Ec '^[[:space:]]+val[[:space:]]+[A-Za-z0-9_]+'
sed -n '/LauncherUiState(/,/^)/p' app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherUiStateMapper.kt \
  | grep -Ec '^[[:space:]]+[A-Za-z0-9_]+[[:space:]]*='
```

### 2.3 盘点记号

为让 108 行盘点保持可读，后续表格使用以下缩写；每个缩写都指向当前提交中的明确文件。

| 记号 | 含义与主要文件 |
|---|---|
| `T/<方法>` | `LauncherStateTransitions.<方法>` |
| `MA` | `app/MainActivity.kt` |
| `HOST` | `launcher/LauncherRootHost.kt` 及全局 Header/路由同步 |
| `DRAW` | `DrawerScreen`、`AppManagementScreen`、Drawer policy/model |
| `SET` | `SettingsScreen`、`SettingsMenuModel` 和字体/布局模型 |
| `SMS` | `SmsController`、SMS screens、`SmsScrollSyncPolicy` |
| `PHONE` | `CallController`、`ContactsController`、拨号/联系人 screens |
| `HOME` | `HomeScreen`、`HomeInfoModel`、`HomeInfoDetailModel` 和 Idle 展示 |
| `HEALTH` | `DataHealthModel` / `DataHealthScreen` |
| `DIAG` | `DiagnosticsModel` / `DiagnosticsScreen` |
| `EFFECT` | `IdleAutoEntryPolicy`、Idle/Pixel Matter/Sand Clock 运行逻辑 |
| `VM` | 只由 Activity 级 ViewModel 保留；配置变更可保留，进程死亡不可保留 |
| `Prefs(...)` | 从对应 SharedPreferences 仓库恢复并单独持久化；状态对象本身不序列化 |
| `Provider/Repo` | 可从 Android provider、系统回调或仓库重新装载 |

“主要读取者”不罗列同一调用链上的每一个中转函数；列出决定行为或渲染的消费者。“耦合/约束”列记录跨目标切片关系或需要特别保护的不变量。

## 3. 108 字段完整盘点

### 3.1 Shell（6）

| 字段 | 当前写入者/转换入口 | 主要读取者 | 寿命/来源 | 耦合/约束 |
|---|---|---|---|---|
| `mode` | 页面级 `T/show*` / `T/hide*` 流程 | `MA`、`HOST`、所有 controller/policy | `VM` | 全域路由枢纽；只能由 flow transition 写 |
| `returnMode` | `T/showSettings`、`T/hideSettings`、`T/showAppManagement`、`T/showIdle`、Phone/SMS 打开/关闭流程 | 转换器内部 | `VM` | 不是 render 字段；必须与 `mode` 成对验收 |
| `statusBarMessageText` | `T/updateStatusBarMessage`、`T/updateStatusBarAction` | `MA` 清除计时、`HOST` | `VM` | 与后三个 action 字段互斥 |
| `statusBarActionLeadingText` | `T/updateStatusBarMessage`、`T/updateStatusBarAction` | `HOST` | `VM` | action 原子组成员 |
| `statusBarActionLabel` | `T/updateStatusBarMessage`、`T/updateStatusBarAction` | `HOST` | `VM` | action 原子组成员 |
| `isStatusBarActionDanger` | `T/updateStatusBarMessage`、`T/updateStatusBarAction` | `HOST` | `VM` | action 原子组成员 |

### 3.2 App catalog / Drawer（17）

| 字段 | 当前写入者/转换入口 | 主要读取者 | 寿命/来源 | 耦合/约束 |
|---|---|---|---|---|
| `apps` | `T/withApps` | `MA`、`DRAW`、`SET` | `Repo/cache` + `VM` | Settings 用它决定 App Management 是否可用 |
| `drawerVisibleApps` | `T/withApps`、`T/showAppDrawer`、`T/updateDrawerQuery`、清空/退出搜索流程 | `MA`、`DRAW`、自动启动策略 | 派生 + `VM` | 应由 `apps + query + recentApps` 推导，迁移期先保留 |
| `drawerQuery` | `T/updateDrawerQuery`、append/backspace/clear/exit | `MA`、`HOST`、`DRAW`、自动启动策略 | `VM` | 与过滤列表、搜索焦点、选择窗口原子更新 |
| `isDrawerSearchFocused` | `T/showHome`、`T/showSettings`、`T/showAppActionMenu`、`T/exitDrawerSearch`、`T/prepareDrawerEntryFocus`、`T/focusDrawerSearchInput`、`T/dismissDrawerOverlaysForPagerDrag` | `MA`、`HOST`、Drawer policy | `VM` | 已封口到具名转换；与菜单/搜索流程原子更新 |
| `isDrawerRailSliding` | `T/showAppActionMenu`、`T/exitDrawerSearch`、`T/prepareDrawerEntryFocus`、`T/focusDrawerSearchInput` | 当前无读取行为 | `VM` | 写而不读，列入独立退役候选 |
| `isAppActionMenuVisible` | `T/show/hideAppActionMenu`、`T/dismissDrawerOverlaysForPagerDrag`、Home/Settings/App Management 流程 | `MA`、`DRAW` | `VM` | 与搜索焦点互斥 |
| `selectedIndex` | Drawer 选择、分页、字母索引、搜索和 reflow 转换 | `MA`、`DRAW` | `VM` | 必须钳制在当前可见列表范围 |
| `listStartIndex` | Drawer 选择和 `syncDrawerWindow` | 转换器内部 | 派生 + `VM` | Host 不消费，列入行为证明后的退役候选 |
| `drawerPageIndex` | Drawer 选择、分页和 `syncDrawerWindow` | 转换器内部 | 派生 + `VM` | Host 不消费，列入行为证明后的退役候选 |
| `drawerFocus` | Drawer 选择和 `syncDrawerWindow` | 当前只有写入，无行为读取 | `VM` | enum 目前仅 `LIST`，优先退役候选 |
| `isLoading` | `T/beginAppCatalogLoading` 写 `true`；`T/withApps` 写 `false` | `DRAW` | `Repo` + `VM` | 只表示应用目录首载，不应与字体/SMS loading 混用 |
| `appEditorSelectedIndex` | App action/menu management 与 editor selection 转换 | `MA`、`DRAW` | `VM` | 必须与 `apps` 同步钳制 |
| `appEditorNameDraft` | App action/menu management 与 draft 转换 | `MA`、`HOST`、`DRAW` | `VM` | 保存后进入 `AppCustomizationRepository`，草稿本身不持久化 |
| `appEditorAliasDraft` | App action/menu management 与 draft 转换 | `MA`、`HOST`、`DRAW` | `VM` | 与姓名草稿同一编辑会话 |
| `recentApps` | `T/updateStats` | Drawer 排序转换、`DIAG` | `Prefs(stats)` + `VM` | 同时是抽屉排序输入，归 AppCatalog 而非泛化 Home |
| `launchCount` | `T/updateStats` | `DIAG` | `Prefs(stats)` + `VM` | 诊断读取，不驱动导航 |
| `lastLaunchPackageName` | `T/updateStats` | `DIAG` | `Prefs(stats)` + `VM` | 与 launch count/recent apps 同一仓库快照 |

### 3.3 Settings（11）

| 字段 | 当前写入者/转换入口 | 主要读取者 | 寿命/来源 | 耦合/约束 |
|---|---|---|---|---|
| `settingsSelectedIndex` | `T/showSettings`、select/move/scroll/reflow settings | `SET` | `VM` | `SettingsMenuModel.rows(state)` 的动态行数决定合法范围 |
| `settingsListStartIndex` | scroll/reflow 和 `syncSettingsWindow` | 转换器内部 | 派生 + `VM` | Host 不消费，列入行为证明后的退役候选 |
| `selectedPixelShape` | `T/updateAppearance` | `MA` profile、`SET` | `Prefs(font)` + `VM` | 与 dot size/gap 的预览确认流程耦合 |
| `selectedDotSizePx` | `T/updateAppearance` | `MA` profile、`SET` | `Prefs(font)` + `VM` | 改变所有页面可见行数，必须触发各域 reflow |
| `isPixelGapEnabled` | `T/updateAppearance` | `MA`/`HOST`、`SET` | `Prefs(font)` + `VM` | Host 像素输出设置 |
| `selectedTheme` | `T/updateAppearance` | `MA`/`HOST`、`SET` | `Prefs(font)` + `VM` | `MA.selectedTheme` 仍有一份镜像，需消除双写 |
| `fontSelection` | `T/updateAppearance` | `MA`、`HOST`、全部列表布局和 `SET`/`DIAG` | `Prefs(font)` + `VM` | 最大跨域布局输入；字体、profile、可见行必须原子切换 |
| `isFontLoading` | `T/updateFontLoading` | `SET`、`DIAG` | `VM` | `MA.isFontLoading` 另有操作锁；两者语义应明确后合并或改名 |
| `fontCacheSummary` | `T/updateFontCacheSummary` | `DIAG` | Repo 诊断 + `VM` | 不参与字体选择正确性 |
| `drawerListAlignment` | `T/updateUiBehavior` | `HOST`、`DRAW`、`SET` | `Prefs(font)` + `VM` | 虽影响 Drawer，写入来源和设置事务属于 Settings |
| `openDrawerInSearchMode` | `T/updateUiBehavior` | `MA` Drawer 入口、`SET` | `Prefs(font)` + `VM` | Settings → AppCatalog 的只读配置依赖 |

### 3.4 SMS（25）

| 字段 | 当前写入者/转换入口 | 主要读取者 | 寿命/来源 | 耦合/约束 |
|---|---|---|---|---|
| `unreadSmsEntries` | `T/updateUnreadSmsEntries`、`T/beginForcedSmsRefresh` | `SMS`、`HOST` 状态栏 | Provider + `VM` | 与 unread page/selection 同步 |
| `smsPageIndex` | `T/showSmsThreads`、`T/selectSmsPage`、unread 回填 | `SMS`、`HOST` | `VM` | unread 为空且非 loading 时会切 ALL |
| `smsSelectedIndex` | select/move/reflow unread SMS | `SMS`、`HOST` | `VM` | 受 unread list 大小约束 |
| `smsListStartIndex` | `syncSmsWindow` | 转换器内部 | 派生 + `VM` | Host 不消费，列入行为证明后的退役候选 |
| `smsThreads` | `T/updateSmsThreads`、`T/beginForcedSmsRefresh` | `SMS` | Provider + `VM` | 与 thread selection/menu 约束 |
| `isSmsThreadsLoading` | `T/beginForcedSmsRefresh`、`T/finishSmsThreadsLoading` | `SMS` | Provider 操作态 + `VM` | 强制刷新开始、能力不足或数据落地时结束 |
| `smsThreadSelectedIndex` | select/move/reflow/search query、`T/moveSmsSearchSelection` | `SMS`、`HOST` | `VM` | 搜索结果数量与会话数量使用不同上限 |
| `smsThreadListStartIndex` | `syncSmsThreadWindow`、搜索 query 重置 | 转换器内部 | 派生 + `VM` | Host 不消费，列入行为证明后的退役候选 |
| `smsAllMessages` | `T/updateSmsAllMessages`、`T/beginForcedSmsRefresh` | `SMS` 搜索 | Provider + `VM` | 搜索结果的唯一数据源 |
| `smsCurrentConversationKey` | show/update SMS detail | `SmsController`、转换器 | Provider identity + `VM` | 菜单、静音、异步回填的会话一致性键 |
| `smsCurrentConversationTitle` | show/update SMS detail | `SMS`、`HOST` 标题 | Provider + `VM` | 与 key/address 同次更新 |
| `smsCurrentIsServiceConversation` | show/update SMS detail | `MA`、`SMS` | Provider + `VM` | 控制服务会话发送能力 |
| `smsCurrentThreadId` | show/update SMS detail | `SmsController`、SMS scroll policy | Provider identity + `VM` | nullable；新会话发送后可能建立 |
| `smsCurrentAddress` | show/update SMS detail | `SmsController`、`HOST` | Provider identity + `VM` | 与 conversation key 不可部分更新 |
| `smsMessages` | show detail 清空、`T/updateSmsMessages` | `SmsController`、detail screen/policy | Provider + `VM` | 旧会话异步结果不可覆盖新会话 |
| `smsThreadSearchQuery` | `T/updateSmsThreadSearchQuery`、关闭模块 | `SMS`、`HOST` | `VM` | 更新时重置 thread selection/window |
| `smsDraftText` | `T/updateSmsDraftText`、打开/关闭 detail/module | `MA`、`SmsController`、`HOST` | `VM` | 发送状态变化与草稿清空需原子化 |
| `smsSendStatus` | `T/updateSmsSendStatus`、detail/module flow | `SmsController`、detail screen | `VM` | 与 draft、重试逻辑耦合 |
| `isSmsMessageMenuVisible` | show/hide message menu、detail flow | `MA`、`SMS` | `VM` | 与目标 message id 成对 |
| `smsMessageMenuMessageId` | show/hide message menu、detail flow | `SMS` | `VM` | 不可指向当前消息列表外 id |
| `isSmsThreadMenuVisible` | show/hide thread menu、page/detail/module flow | `MA`、`SMS` | `VM` | 与 conversation key 成对 |
| `smsThreadMenuConversationKey` | show/hide thread menu、page/detail/module flow | `SMS` | `VM` | 不可指向当前会话列表外 key |
| `smsMutedConversationKeys` | `T/updateSmsMutedConversations` | `SmsController`、thread screen | `Prefs(sms-mute)` + `VM` | 含通信身份，仓库另有备份排除策略 |
| `isDefaultSmsApp` | `T/updateSmsCapability` | `SmsController`、`HEALTH` | 系统能力 + `VM` | 与 `smsPermissionState` 同次采样 |
| `smsPermissionState` | `T/updateSmsCapability` | `SmsController`、`HEALTH` | 系统能力 + `VM` | `MISSING/READ_ONLY/READY` 不等价于单一布尔值 |

### 3.5 Phone / Contacts（13）

| 字段 | 当前写入者/转换入口 | 主要读取者 | 寿命/来源 | 耦合/约束 |
|---|---|---|---|---|
| `callLogGroups` | `T/updateCallLogGroups` | `CallController`、call log screen | Provider + `VM` | 回填时结束 loading |
| `isCallLogLoading` | `T/prepareCallLogLoading`、`T/updateCallLogGroups` | call log screen | Provider 操作态 + `VM` | 无权限或已有缓存时不展示首次 loading |
| `hasCallPhonePermission` | `T/updateCallCapability` | `CallController` | 系统能力 + `VM` | 当前与 `System.hasCallLogPermission` 被同一转换跨域写 |
| `callPageIndex` | show/hide call、select call page、contact flow | `MA`、`HOST`、dialer screen | `VM` | 缺 call-log 权限时入口切到 dial page |
| `dialInput` | `T/updateDialInput`、关闭 call | `CallController`、dialer screen | `VM` | 输入变化必须先清旧匹配 |
| `dialMatches` | `T/updateDialInput`、`T/updateDialMatches`、关闭 call | dialer screen | 异步派生 + `VM` | 只有 query 仍相同才接受异步结果 |
| `contacts` | `T/updateContacts` | controllers、联系人 screens、`HOST` 标题 | Provider + `VM` | detail 只存 lookup key，实体从此列表解析 |
| `isContactsLoading` | `T/beginContactsLoading`、`T/updateContacts` | contacts screen | Provider 操作态 + `VM` | 有旧数据时静默刷新 |
| `hasContactsPermission` | `T/updateContacts` | contacts screen | 系统能力 + `VM` | 与 contacts 同次回填 |
| `contactDetailLookupKey` | show/hide detail、hide editor | `HOST`、contacts screen | `VM` | 不复制联系人实体，保持单一真值 |
| `contactEditorLookupKey` | show/hide editor | `ContactsController`、editor screen | `VM` | 空串表示新建 |
| `contactEditorNameDraft` | show/hide/update editor | `ContactsController`、`HOST`、editor screen | `VM` | editor 会话瞬态 |
| `contactEditorNumberDraft` | show/hide/update editor | `ContactsController`、`HOST`、editor screen | `VM` | 编辑既有联系人时空串表示不新增号码 |

### 3.6 Effects / Idle（10）

| 字段 | 当前写入者/转换入口 | 主要读取者 | 寿命/来源 | 耦合/约束 |
|---|---|---|---|---|
| `isIdlePageEnabled` | `T/updateUiBehavior` | `MA`、`SET`、`EFFECT` | `Prefs(font)` + `VM` | 禁用时必须退出 Idle |
| `chargeAutoIdleEnabled` | `T/updateUiBehavior` | `MA`、`SET`、`EFFECT` | `Prefs(font)` + `VM` | 读取 `System.isCharging` |
| `inactivityAutoIdleEnabled` | `T/updateUiBehavior` | `MA`、`SET`、`EFFECT` | `Prefs(font)` + `VM` | 读取 interaction uptime |
| `idleTimeoutSeconds` | `T/updateUiBehavior` | `MA`、`SET`、`EFFECT` | `Prefs(font)` + `VM` | 必须经 `IdleSettings` 归一化 |
| `chargeIdleEffect` | `T/updateUiBehavior` | `SET`、Idle 状态模型 | `Prefs(font)` + `VM` | 仅决定充电 Idle 展示 |
| `isPixelMatterEffectEnabled` | `T/updateUiBehavior` | `MA`、`SET` | `Prefs(font)` + `VM` | 影响 motion listener 生命周期 |
| `pixelMatterEffectMode` | `T/updateUiBehavior` | `MA`、`HOST`、`SET` | `Prefs(font)` + `VM` | 影响 effect 模拟器选择 |
| `isPixelMatterHandControlEnabled` | `T/updateUiBehavior` | `MA`、`SET` | `Prefs(font)` + `VM` | 依赖 camera 权限与 Activity resumed 状态 |
| `isPixelMatterHandDebugEnabled` | `T/updateUiBehavior` | `MA`、`SET` | `Prefs(font)` + `VM` | 影响 Android debug overlay |
| `lastInteractionUptimeMs` | `T/recordInteraction` | `MA`、`IdleAutoEntryPolicy` | `VM`（单调时钟） | 不能持久化或跨进程恢复为旧 uptime |

### 3.7 Home feed（11）

| 字段 | 当前写入者/转换入口 | 主要读取者 | 寿命/来源 | 耦合/约束 |
|---|---|---|---|---|
| `currentTimeText` | `T/updateTime` | `HOST`、HOME/Idle | 系统时钟 + `VM` | Header 与 Idle 共用 |
| `currentDateText` | `T/updateTime` | HOME/Idle | 系统时钟 + `VM` | 与 time/weekday 同次刷新 |
| `currentWeekdayText` | `T/updateTime` | Idle screen | 系统时钟 + `VM` | 与 time/date 同次刷新 |
| `nextAlarmText` | `T/updateNextAlarmText` | HOME/Idle | 系统回调 + `VM` | 可重新订阅恢复 |
| `missedCallCount` | `T/updateCommunicationStatus` | HOME/Idle | Provider + `VM` | Phone 数据源、Home 展示所有权 |
| `unreadSmsCount` | `T/updateCommunicationStatus` | `MA`、`SmsController` 入口、HOME/Idle | Provider + `VM` | SMS 打开初始页依赖该计数 |
| `mediaPlayback` | `T/updateMediaPlayback` | `MA`、`HOST`、Home screen | Repo callback + `VM` | 当前 render 还会主动轮询并写回，需移出 render |
| `rainHintText` | `T/updateRainHintText` | HOME/Idle | 网络/位置 repo + `VM` | 与位置能力、更新时间关联 |
| `rainUpdatedTimeText` | `T/updateRainHintText` | `MA`、Home detail | 运行时刷新 + `VM` | 失败保留旧摘要时不能伪造成功时间 |
| `screenUsageTimeText` | `T/updateScreenUsageSummary` | HOME/Idle/detail | usage repo + `VM` | 与 usage access 相关 |
| `screenOpenCountText` | `T/updateScreenUsageSummary` | HOME/detail | usage repo + `VM` | 与 usage time 同次回填 |

### 3.8 Notification（6）

| 字段 | 当前写入者/转换入口 | 主要读取者 | 寿命/来源 | 耦合/约束 |
|---|---|---|---|---|
| `notificationSummaryText` | `T/updateNotificationSummary` | HOME/Idle | listener store + `VM` | 与 count/sources/items 同一发布快照 |
| `notificationCount` | `T/updateNotificationSummary` | HOME/Idle | listener store + `VM` | 非负归一化 |
| `notificationSources` | `T/updateNotificationSummary` | `MA`、notification settings | listener store + `VM` | 包含 muted 来源，供规则配置和单来源跳转 |
| `notificationItems` | `T/updateNotificationSummary` | `MA`、Home screen | listener store + `VM` | 点击 action 的 key/index 来自此快照 |
| `mutedNotificationSourceIds` | `T/updateNotificationRules` | `SET`、notification settings | `Prefs(notify)` + `VM` | 规则变化会触发 store 重新发布摘要；与 priority 互斥 |
| `priorityNotificationSourceIds` | `T/updateNotificationRules` | `SET`、notification settings | `Prefs(notify)` + `VM` | 更新时减去 muted 集合并触发摘要重排 |

四个实时字段与两个持久规则字段允许由两个事件分别更新，但所有权同属 Notification。规则事件必须先规范化并持久化，再交给 `NotificationSummaryStore.updateRules()`；store 基于当前 signals 重新计算四个实时字段。验收关注最终一致性，不要求六字段在同一个 `copy` 中同步改变。

### 3.9 System / Capabilities（9）

| 字段 | 当前写入者/转换入口 | 主要读取者 | 寿命/来源 | 耦合/约束 |
|---|---|---|---|---|
| `batteryLevel` | `T/updateDeviceStatus` | `HOST`、HOME/Idle、`DIAG` | 系统回调 + `VM` | 全局共享，不能复制到 Home/Effect |
| `isCharging` | `T/updateDeviceStatus` | `MA`、`HOST`、HOME/Idle、`EFFECT`、`DIAG` | 系统回调 + `VM` | 充电变化可能触发 Idle flow |
| `hasUsageAccess` | `T/updateDataHealth` | `MA`、HOME detail、`HEALTH`、`DIAG` | 系统能力采样 + `VM` | 当前 render 路径重复采样 |
| `hasLocationPermission` | `T/updateDataHealth` | `MA`、HOME、`HEALTH` | 系统能力采样 + `VM` | 天气刷新守卫 |
| `hasCallLogPermission` | `T/updateDataHealth` 和 `T/updateCallCapability` | `MA`、`CallController`、call log screen、`HEALTH` | 系统能力采样 + `VM` | 当前双写；目标只由 capability coordinator 写 |
| `hasSmsReadPermission` | `T/updateDataHealth` | `MA`、`HEALTH` | 系统能力采样 + `VM` | 与 SMS 三态不同：只描述 READ_SMS |
| `hasPostNotificationPermission` | `T/updateDataHealth` | `MA`、`HEALTH` | 系统能力采样 + `VM` | 平台版本条件由采样端处理 |
| `hasNotificationListenerAccess` | `T/updateDataHealth` | `MA`、`HEALTH` | 系统能力采样 + `VM` | 与通知数据是否已回填不是同一状态 |
| `dataHealthUpdatedTimeText` | `T/updateDataHealth` | `MA`、`HEALTH` | `VM` | 只在用户显式刷新时更新时间 |

字段计数校验：Shell 6 + AppCatalog 17 + Settings 11 + SMS 25 + Phone 13 + Effect 10 + Home 11 + Notification 6 + System 9 = **108**。

## 4. 当前高耦合点与风险

### 4.1 identity mapper 不是有效边界

`LauncherUiStateMapper.kt:5-114` 对 108 个字段逐一同名复制，没有任何转换。`LauncherUiStateMapperTest` 只有 2 个测试；按字段标识符核对，测试源码提到 62 个字段，另有 46 个字段没有被该测试直接覆盖。默认态相等测试不能发现两个类型同时增加相同默认值却漏掉非默认映射的情况。

当前还出现 `launcher` 包的 presentation model 同时为 `LauncherState` 和 `LauncherUiState` 提供重复 overload，例如 `DataHealthModel` 和 `DiagnosticsModel`。这形成 `launcher -> viewmodel` 的反向概念依赖，并扩大每次字段调整的修改面。

### 4.2 转换器既是 reducer，也是路由流程和搜索算法容器

`LauncherStateTransitions` 的 100 个公开方法混合了四种职责：

1. 单域字段更新，例如 `updateTime`、`updateMediaPlayback`。
2. 窗口/选择不变量，例如 `syncDrawerWindow`、`syncSmsThreadWindow`。
3. 跨域流程，例如 `showSmsThreadDetail` 同时改 Shell 和 SMS。
4. 搜索排序算法，例如 drawer metadata、拼音与 recent boost。

高耦合转换包括：

- `showHome` / `showSettings`：Shell + Drawer，且 Settings 行模型还读取 AppCatalog、Effect、Notification。
- `showAppManagement`：Shell + AppCatalog。
- `showIdle`：写 Shell，读取 Effect 与当前 Shell；充电自动进入又读取 System。
- SMS/Phone/Contact 的 `show*` / `hide*`：Shell + 对应业务域。
- `updateUiBehavior`：同时写目标 Settings 与 Effect。
- `updateCallCapability`：同时写目标 Phone 与 System。
- `updateNotificationSummary` / `updateNotificationRules`：分别接收实时 store 与持久规则输入，但共同写目标 Notification；规则变化还会触发 summary 二次发布。

目标不是禁止跨域流程，而是把它们集中到 `LauncherFlowTransitions`；单域 reducer 只写自己拥有的切片，flow 通过组合单域 reducer 完成原子跨域变化。

### 4.3 reducer 外聚合 `copy` 已按 13 → 8 → 0 清零

阶段 0 建立契约时，生产代码有 13 个绕过 `LauncherStateTransitions` 的聚合
`LauncherState.copy` 表达式：

- `MainActivity` 8 处：应用 loading、pager fallback mode、Drawer 菜单/搜索焦点和 rail 状态（`1197`、`1342`、`1351`、`1362`、`1789`、`1822`、`1957`、`1999`）。
- `SmsController` 4 处：SMS loading/清空、搜索态选择和数据回填（`201`、`203`、`466`、`886`）。
- `CallController` 1 处：Call Log loading（`106`）。

阶段 0 不能一边保留这 13 处，一边实施“零容忍”扫描。可执行做法是建立精确 allowlist/baseline，
以“文件 + 所在方法 + 被复制字段集合 + 表达式数量”为键，行号只用于人工定位。历史基线如下：

| 文件 / 方法 | 允许表达式数 | 阶段 0 允许字段 |
|---|---:|---|
| `MainActivity.loadApps` | 1 | `isLoading` |
| `MainActivity.onMainPageChanged` | 2 | `mode`；`isDrawerSearchFocused`、`isDrawerRailSliding` |
| `MainActivity.onMainPageDragStart` | 1 | `isDrawerSearchFocused`、`isAppActionMenuVisible` |
| `MainActivity.showAppDrawer` | 1 | `isDrawerSearchFocused`、`isDrawerRailSliding` |
| `MainActivity.onPixelEngineDrawerQueryChanged` | 1 | `isDrawerSearchFocused`、`isDrawerRailSliding` |
| `MainActivity.handleDrawerTextInput` | 2 | `isDrawerSearchFocused`、`isDrawerRailSliding` |
| `SmsController.openModule` | 2 | `isSmsThreadsLoading`；`unreadSmsEntries`、`smsThreads`、`smsAllMessages`、`isSmsThreadsLoading` |
| `SmsController.moveThreadSelection` | 1 | `smsThreadSelectedIndex` |
| `SmsController.applySmsData` | 1 | `isSmsThreadsLoading` |
| `CallController.openCallLog` | 1 | `isCallLogLoading` |
| **合计** | **13** | MainActivity 8 / SmsController 4 / CallController 1 |

阶段 0 的契约允许且只允许上表签名：新增文件/方法、新增表达式、给现有表达式增加字段，或总数不再是 13，均立即失败。阶段 1 每替换一处就同步缩减 allowlist，全部替换后才启用空 allowlist 的零容忍规则。不得用易漂移的绝对行号作为机器匹配条件。

阶段 1 先将 `SmsController` 4 处和 `CallController` 1 处迁移到具名转换，形成 **13 → 8** 的中间进度。
该中间 allowlist 只剩 `MainActivity` 的 7 个基线身份、8 个表达式：

| 文件 / 方法 | 当前允许表达式数 | 当前允许字段 |
|---|---:|---|
| `MainActivity.loadApps` | 1 | `isLoading` |
| `MainActivity.onMainPageChanged` | 1 | `isDrawerSearchFocused`、`isDrawerRailSliding` |
| `MainActivity.onMainPageChanged` | 1 | `mode` |
| `MainActivity.onMainPageDragStart` | 1 | `isDrawerSearchFocused`、`isAppActionMenuVisible` |
| `MainActivity.showAppDrawer` | 1 | `isDrawerSearchFocused`、`isDrawerRailSliding` |
| `MainActivity.onPixelEngineDrawerQueryChanged` | 1 | `isDrawerSearchFocused`、`isDrawerRailSliding` |
| `MainActivity.handleDrawerTextInput` | 2 | `isDrawerSearchFocused`、`isDrawerRailSliding` |
| **合计** | **8** | 7 个基线身份，全部位于 MainActivity |

随后 `MainActivity` 的 7 个可达表达式均改由具名转换承接，不可达 Pager fallback
`else -> state.copy(mode = mode)` 收窄为 `else -> state`。当前进度为 **8 → 0**，
`tools/launcher-state-copy-baseline.json` 的 `entries` 已为空；任何新的生产聚合直接 `copy`
都会被零容忍门禁作为未登记写入拒绝。

此外三个 controller 的 `Host` 都暴露可读写的完整 `LauncherState`（`MainActivity.kt:178-257`），
因此类型层面仍无法阻止未来修改其他域。Controller 5 处已改为通过具名转换产生状态；待剩余
`MainActivity` 写入也已清零，controller Host 的类型收窄仍是后续任务，尚未实施。

### 4.4 `MainActivity` 在 render 中写状态

`MainActivity.renderCurrentFrame()`（`1271-1293`）先调用 `refreshDataHealthState()`，又读取 `mediaPlaybackRepository.current()` 并可能更新状态，最后才映射和提交 Host。因此 render 不是幂等读操作；任何触发重绘的路径都隐式触发能力采样和状态写入。

目标必须拆成两个边界：数据/能力刷新产生事件，render 只读取已提交的快照。验收应明确“连续调用 render 不访问 repository、不改变 state”。

### 4.5 生命周期不是持久化策略

`LauncherViewModel` 没有 `SavedStateHandle`，`MainActivity` 也没有 `onSaveInstanceState` 状态恢复。所有 `VM` 字段只能跨配置变更，不能跨进程死亡。持久字段由四类仓库独立恢复：

- `FontSettingsRepository`：外观、字体、Drawer 偏好和 Effect/Idle 偏好。
- `LauncherStatsRepository`：recent、launch count、last package。
- `NotificationSummarySettingsRepository`：通知 muted/priority 规则。
- `SmsMuteSettingsRepository`：短信会话静音规则。

结构迁移不得顺手引入全量状态序列化。特别是 `lastInteractionUptimeMs` 使用单调时钟，不能跨进程持久化；provider/repository 快照应重新装载。若产品需要恢复页面/草稿，应另立 ADR 定义白名单和安全边界。

`MainActivity` 还维护与状态相邻的操作变量：`selectedTheme`、`isFontLoading`、像素外观确认 baseline/deadline、`launchPending`、天气刷新信息、motion/hand tracking 标志。尤其预览后的 appearance 状态可由 ViewModel 跨配置保留，而 rollback baseline 仍在 Activity 字段中，重建中存在不一致风险；必须在 Settings/Effect 拆分阶段加入配置重建用例。

### 4.6 不应把所有 108 个字段原样永久保留

静态引用核对发现 7 个字段没有 `LauncherState`、`LauncherUiState`、mapper、transitions 之外的生产消费者：`listStartIndex`、`drawerPageIndex`、`drawerFocus`、`returnMode`、`settingsListStartIndex`、`smsListStartIndex`、`smsThreadListStartIndex`。其中 `returnMode` 是转换器内部需要的导航记忆，不是死字段；其余窗口字段应在独立任务中验证 Engine scroll controller 已完全接管后再考虑删除。

`isDrawerRailSliding` 更明显：生产代码只反复写 `false`，没有读取点。它和 `drawerFocus` 是优先退役候选。但状态切片任务的第一目标是结构等价，禁止在同一提交顺手删除这些字段；删除必须有独立测试和独立回滚点。

## 5. 目标依赖规则

1. `LauncherState` 只聚合九个不可变切片；切片之间不互相持有，也不依赖 Android 或 `data` 包。
2. repository/controller 产生输入事件；单域 reducer 只能返回自己拥有的切片，不能写其他切片。
3. `LauncherFlowTransitions` 是唯一允许原子组合多个切片的纯函数层，例如“打开短信详情”同时更新 Shell 与 SMS。
4. 阶段 0 只允许上述 13 处精确 baseline 且禁止增长；阶段 1 完成后，`MainActivity`、controller 和 Screen 禁止直接调用聚合状态的 `copy`。Activity 负责 Android 生命周期、系统结果桥接和 Host 挂载，不拥有业务规则。
5. `SystemState` 由平台能力协调器单写，Home、Phone、SMS、Effect 和 diagnostics 只读。`hasCallLogPermission` 的现有双写必须收敛。
6. `NotificationState` 由 notification coordinator 单写；listener summary 与持久规则是同域的两个事件源，Home、Settings、Idle 和 Notification Settings 只读。
7. Screen 优先接收单个切片或明确的只读 input，而不是 108 字段聚合对象。组合页面（Settings、Diagnostics、Data Health、全局 Header）可以在 Host/presentation 层显式组合多个只读切片。
8. 状态变更先提交，再 render；render 不触发 I/O、权限采样或 reducer。
9. 持久化 DTO 与运行状态分开。切片 data class 不直接序列化 SharedPreferences，也不保存 Android provider 实体生命周期。

## 6. 分阶段迁移任务

每一阶段独立提交、独立验收，失败时回滚该阶段提交，不跨阶段修补。所有新增类、字段、变量和方法按仓库要求添加必要中文说明。

### 阶段 0：建立 108 字段行为基线

**范围**

- 新增可复核的 flattened snapshot 测试：为 108 个字段设置可区分值，并逐字段验证 mapper；集合/list/id 使用非空哨兵。
- 为现有 100 个公开 transition 建立按域索引；补齐当前关键 flow 的 before/after 快照测试。
- 新增静态契约，精确 allowlist 上述 13 个聚合 `LauncherState.copy`；禁止数量、位置或字段集合增长，并在失败信息中输出新增签名。

**禁止项**

- 不改变状态形状、默认值、导航结果或持久化。
- 不在本阶段修改 13 个既有表达式，也不把 baseline 泛化成“整个文件允许”。
- 不以“默认状态相等”代替非默认字段验证。

**行为等价验收**

- 108 个源字段、108 个 UI 字段、108 个 mapper assignment 数量相等。
- 聚合 `copy` baseline 精确为 13：MainActivity 8、SmsController 4、CallController 1；模拟第 14 处或给现有表达式追加字段时契约必须失败。
- 每个字段在非默认输入下通过投影；现有 app JVM 测试全绿。

**回滚点**

- 单独提交测试/契约；若契约误报，只回滚本阶段，不影响生产行为。

**测试建议**

- `LauncherUiStateMapperTest`、`LauncherStateTransitionsTest`、`DrawerQueryTransitionsTest`。
- 增加一个字段清单测试，明确失败时打印缺失/重复字段名。

### 阶段 1：封闭 reducer 外写入口并纯化 render

**范围**

- 为上述 12 个可达直接 `copy` 引入命名 transition/event：App/SMS/Call loading、Drawer focus/menu、搜索态选择；不可达 pager fallback 直接删除，不引入任意 mode setter。
- 把 `refreshDataHealthState()` 与 media polling 移出 `renderCurrentFrame()`；由明确的 lifecycle/callback 刷新入口提交状态。
- controller Host 暂时仍可读聚合状态，但写入改为 dispatch；不再暴露任意 setter。

**禁止项**

- 不拆文件、不引入嵌套切片、不改变权限请求时机或异步线程模型。
- 不顺手调整文案、搜索排序、列表选中规则。

**行为等价验收**

- 12 个可达写入的 before/after snapshot 与基线完全一致，并保留 1 个 Pager fallback 不可达证据。
- 13 处已收敛为 0：12 个可达写入完成替换，1 个死 fallback 删除；allowlist 为空，生产代码中 transition/flow 之外的聚合 `LauncherState.copy` 为 0。
- 连续 render 两次，state 相等且 fake repository 调用数不增加。
- 强制刷新 SMS、Call 首次 loading、Drawer 键盘/搜索焦点路径通过现有测试及新增单测。

**回滚点**

- “命名 transition”与“render 纯化”分两个提交；可分别回滚。

**测试建议**

- `SmsController` / `CallController` fake-host 行为测试，不只做源码字符串契约。
- MainActivity render seam 的 JVM 测试；若 Android 依赖无法 JVM 化，先把纯调度对象抽出再测。

### 阶段 2：按域拆分 transition 文件，状态仍保持扁平

**范围**

- 将 1,657 行转换器拆成 Shell/Flow、AppCatalog、Settings、SMS、Phone、Effect、Home、Notification、System 九组纯转换文件。
- Drawer 搜索 metadata/排序 helper 移入 AppCatalog 组。
- 暂留 `LauncherStateTransitions` 作为薄 facade，原调用点不必同提交全部改名。

**禁止项**

- 不改变任何字段、算法、默认值或调用顺序。
- 不在 move 过程中“清理”7 个无外部消费者字段。

**行为等价验收**

- 阶段 0 的全量 transition snapshot 基线不变。
- facade 与新 domain transition 对同一输入返回完全相等状态。
- Drawer 搜索的中文、拼音、别名、recent boost 测试全绿。
- Notification summary/rules 转换对空列表、muted/priority 冲突和规则重排保持当前结果。

**回滚点**

- 单一机械搬迁提交；不夹带功能修改。

**测试建议**

- 现有 68 个 `LauncherStateTransitionsTest`、8 个 `DrawerQueryTransitionsTest` 全量执行。

### 阶段 3：由 schema integrator 逐个引入规范化切片

**范围与顺序**

只有一个 schema integrator 可修改 `LauncherState.kt`、`LauncherUiState.kt` 和 mapper。每个切片采用“加入嵌套值 → 提供只读兼容访问器 → 域 agent 迁移读写 → integrator 删除兼容访问器”的两提交节奏。切片严格串行：

1. `PhoneState`（13）作为低风险试点。
2. `SmsState`（25），利用现有 `SmsController` 边界。
3. `NotificationState`（6），利用现有 store/listener 和 settings repository 边界。
4. `AppCatalogState`（17）。
5. `SettingsState`（11）。
6. `EffectState`（10）。
7. `HomeState`（11）。
8. `SystemState`（9），并收敛 `hasCallLogPermission` 单写。
9. `ShellState`（6）最后迁移，因为所有 flow 都依赖它。

**禁止项**

- 同一时间不得有两个 agent 修改三个 schema 共享文件。
- 域 agent 不修改 schema 文件；schema integrator 不改 controller/screen 行为。
- 不把兼容访问器做成第二份可写状态；它们只能从嵌套值读取。
- 不在切片迁移中新增持久化或改变默认值。

**行为等价验收**

- 每个切片迁移后 flattened snapshot 仍有 108 个唯一字段且值与迁移前相等。
- data class equality/copy 的域内变更只改变对应切片；其他八个切片保持引用或值相等。
- 对应域 transition、controller、screen/model 测试全绿。

**回滚点**

- 每个切片两个独立提交；可先回滚域调用迁移，再回滚 schema 接入。

**测试建议**

- Phone：Call/Contacts transition、T9、controller fake-host、联系人 CRUD presentation。
- SMS：列表/搜索/发送/菜单/静音、异步旧会话防覆盖和 scroll sync。
- Notification：signals/rules 汇总、muted/priority 冲突、规则改变后的同步重新发布、Home 点击和来源设置。
- AppCatalog：缓存、搜索、分页/选择、编辑草稿和启动统计。
- Settings/Effect：偏好 round-trip、字体切换、preview confirm/rollback、Idle 计时和 camera/motion 生命周期。
- Home/System：仓库 callback 顺序、天气失败保留、权限变化和 battery-triggered Idle。
- Shell：全 `LauncherMode` 的 open/back/return matrix。

### 阶段 4：收敛 UI 投影和 Host 输入

**范围**

- 由唯一 Host integration agent 修改 `LauncherRootHost.kt`；各 Screen 改为接收所需切片或明确 input。
- 移除重复 model overload；presentation model 依赖纯切片，不依赖 `viewmodel` 包。
- 若没有真实 UI 派生值，删除 `LauncherUiState` 和 `toLauncherUiState()`；若保留 render projection，必须比聚合状态更窄并为每个派生规则测试。

**禁止项**

- 不同时重写 Widget 布局、路由动画或 ScrollController 行为。
- 多个域 agent 不得并行修改 `LauncherRootHost.kt`。

**行为等价验收**

- 所有目的地的输入值与阶段 0 flattened snapshot 对应值一致。
- SMS/Drawer/Phone 的 pager、selection reveal、draft/editor controller，以及 Notification Settings 来源/规则展示行为不变。
- `launcher` / `ui` 不再依赖 `viewmodel.LauncherUiState`。

**回滚点**

- 先提交 narrow Screen inputs，再提交删除 identity mapper；删除提交可独立回滚。

**测试建议**

- RootHost route/page-order、SMS scroll sync、Home/Idle/Diagnostics/Notification Settings presentation 测试。
- 至少跑一次 app instrumentation 的页面输入/恢复 smoke，避免 retained controller 同步回归。

### 阶段 5：按状态边界拆分 MainActivity 编排

**范围**

- 保留已有 `SmsController`、`CallController`、`ContactsController`，将其 Host 改为窄读取 + event dispatch。
- 依次抽出 AppCatalog、Notification、HomeData、Appearance、Effect、Capability coordinator；每个 coordinator 只订阅自己的 repository/lifecycle 输入并 dispatch 对应事件。
- `MainActivity` 最终只负责 Android lifecycle 转发、系统 result/permission bridge、依赖装配、顶层 Host 挂载和 flow dispatch。

**禁止项**

- 不一次性重写 3,009 行 Activity。
- 不让 coordinator 持有 View、Context（确有平台操作时使用窄 gateway），不让 Screen 访问 repository。
- 不改变 executor/Handler 的线程归属或回调丢弃条件。

**行为等价验收**

- onCreate/onResume/onPause/onDestroy 的 start/stop/dispose 次数与原实现一致。
- 旧异步回调不能覆盖新 query/conversation/generation；Activity 销毁后不再提交 UI。
- render 保持纯读；每个 repository 只有一个 lifecycle owner。
- Activity 配置重建时，字体 preview/rollback、SMS/Phone overlay 和 Effect listener 状态有明确测试结果。

**回滚点**

- 每个 coordinator 单独提交，顺序建议 Capability → Notification → HomeData → AppCatalog → Appearance → Effect；任一提交可独立回滚。

**测试建议**

- 使用 fake gateway/repository 的 coordinator JVM 测试。
- 真机/模拟器覆盖冷启动、后台恢复、配置重建、权限变化、短信详情、拨号/联系人、字体预览和 Idle。

### 阶段 6：独立清理遗留派生字段

**范围**

- 对 `isDrawerRailSliding`、`drawerFocus` 和 5 个 list/page start 字段逐个做读写用途证明。
- Engine ScrollController 已承担行为且删除前后交互测试一致时，才删除字段和对应 mapper/transition 逻辑。
- `returnMode` 保留在 Shell，除非新的 typed navigation stack 能完全表达返回语义并通过全 mode matrix。

**禁止项**

- 不把字段删除夹在切片或 Activity 拆分提交中。
- 不因“当前无 Screen 读取”直接删除 transition 所依赖的选择算法。

**行为等价验收**

- 每删一个字段单独提交，Drawer/SMS/Settings 的键盘、手势、pager 和滚动定位测试无变化。
- 108 flattened 基线按明确删除清单递减，不能出现未登记字段丢失。

**回滚点**

- 一字段或一个强内聚字段组一个提交。

**测试建议**

- RootHost ScrollController instrumentation/screenshot smoke；现有 selection/reflow 单测。

## 7. Agent / worktree 文件所有权建议

为避免多个 agent 同时修改超大文件，后续任务使用以下独占规则：

| 角色 | 独占文件 | 可并行文件 |
|---|---|---|
| Schema integrator | `LauncherState.kt`、`LauncherUiState.kt`、`LauncherUiStateMapper.kt` | 新增 domain state 文件、schema tests |
| Transition integrator | 旧 `LauncherStateTransitions.kt`，直到阶段 2 完成 | 新 domain transition tests |
| Host integration agent | `LauncherRootHost.kt` | RootHost/presentation tests |
| Activity flow agent | `MainActivity.kt` | 新 coordinator、gateway 和其测试 |
| SMS agent | `SmsController.kt`、SMS screen/model/policy | SMS tests |
| Phone agent | `CallController.kt`、`ContactsController.kt`、Phone screens/models | Phone tests |
| Notification agent | notification repository/store、model、screen 和新 coordinator | Notification tests；不得修改 `MainActivity.kt` |
| App/Settings/Effect/Home agents | 各自 domain 文件 | 各自 tests；不得触碰上述四类独占文件 |

每个 worktree 从上一个已验收阶段提交创建；未通过验收的分支不作为后续分支基线。禁止并行分支各自重排 `LauncherState` 构造参数后再尝试文本合并。

## 8. 每阶段统一门禁

最低命令：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
git diff --check
```

涉及 Host、生命周期或 Android provider 的阶段，再执行：

```bash
./gradlew :app:assembleDebugAndroidTest
```

并在授权的测试设备上执行对应 instrumentation / 人工核心链路。验收报告必须列出命令、结果、未执行项及原因；不能用“编译通过”替代行为等价证明。

## 9. 采纳结果

采纳本 ADR 后，近期架构工作的判断顺序是：先封住任意写入口并建立 108 字段基线，再拆 transition，随后由单一 schema integrator 串行引入切片；UI identity mapper 和 MainActivity 编排最后收敛。这样每一步都有行为基线、文件所有权和独立回滚点，不需要一次性重写 Launcher。
