# Launcher 状态转换行为基线

- 盘点日期：2026-07-30
- 生产入口：`app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherStateTransitions.kt`
- 目标：在拆分 reducer 前，锁定全部公开入口的领域归属、生产调用、写入面和 JVM 行为证据，并为 reducer 外既有写入准备具名转换
- 非目标：本基线不改变生产行为，不替代 Controller、Activity 或真机链路测试

## 1. 口径与结论

`LauncherStateTransitions` 当前有 **108 个**公开 `fun`。本盘点将跨切片、包含顶层路由的入口标成
`Flow/<领域>`；只写单一切片的入口使用 ADR-0001 的九个切片名：
`Shell`、`AppCatalog`、`Settings`、`Sms`、`Phone`、`Effect`、`Home`、
`Notification`、`System`。

行为覆盖只认可测试中真实调用 `LauncherStateTransitions.<方法>` 并断言返回状态或关键不变量：

| 时点 | 直接行为覆盖 | 仅间接覆盖 | 缺口 |
|---|---:|---:|---:|
| 阶段 0 开始前 | 56 | 0 | 44 |
| 阶段 0 完成后 | 100 | 0 | 0 |
| 本任务完成后 | 108 | 0 | 0 |

这里不把读取源码后做字符串匹配的静态契约算成行为覆盖，也不把“相邻模型有测试”算成 reducer 的间接覆盖。
阶段 0 新增的 11 组测试按行为域组织，检查窗口一致性、路由返回、互斥字段和异步回填不覆盖独立状态。
本任务新增 8 个精确 before/after 用例：每个用例都从含跨域非默认哨兵的状态出发，并以完整 data class
相等性证明只改变约定字段，不是只调用入口来增加数字。

表格缩写：

- `MA`：`MainActivity`
- `CALL`：`CallController`
- `CONTACTS`：`ContactsController`
- `SMS-C`：`SmsController`
- `LST`：既有 `LauncherStateTransitionsTest`
- `DQT`：既有 `DrawerQueryTransitionsTest`
- `Journey`：既有 `LauncherCoreStateJourneyAcceptanceTest`
- `LTB`：新增 `LauncherTransitionBaselineTest`
- `LSW`：新增 `LauncherStateWriteTransitionsTest`
- “无外部调用”表示生产源码在 reducer 对象外没有引用；不等于已经决定删除

## 2. 108 个公开入口完整矩阵

### 2.1 Shell 与跨页 Flow（17）

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 1 | `showHome` | `Flow/Shell` | `mode`、Drawer 搜索焦点/菜单 | `MA` | 既有 `LST#showHome_clearsDrawerSearchFocus` |
| 2 | `showSettings` | `Flow/Settings` | `mode`、`returnMode`、设置焦点/窗口、Drawer 浮层 | `MA` | 既有 `LST#showSettings_fromDrawer_remembersDrawerAsReturnMode` |
| 3 | `hideSettings` | `Flow/Settings` | `mode`、`returnMode`、应用菜单 | `MA` | 既有 `LST#hideSettings_fallsBackToHomeWhenReturnModeIsNonReturnable` |
| 4 | `showSnake` | `Flow/Effect` | `mode` | `MA` | 新增 `LTB#auxiliaryFlows_keepDocumentedShellRoutes` |
| 5 | `hideSnake` | `Flow/Effect` | `mode` | `MA` | 新增 `LTB#auxiliaryFlows_keepDocumentedShellRoutes` |
| 6 | `showDiagnostics` | `Flow/System` | `mode` | `MA` | 既有 `LST#diagnostics_openAndClose` |
| 7 | `hideDiagnostics` | `Flow/System` | `mode` | `MA` | 既有 `LST#diagnostics_openAndClose` |
| 8 | `showDataHealth` | `Flow/System` | `mode` | `MA` | 既有 `LST#dataHealth_openAndClose` |
| 9 | `hideDataHealth` | `Flow/System` | `mode` | `MA` | 既有 `LST#dataHealth_openAndClose` |
| 10 | `showNotificationSettings` | `Flow/Notification` | `mode` | `MA` | 新增 `LTB#auxiliaryFlows_keepDocumentedShellRoutes` |
| 11 | `hideNotificationSettings` | `Flow/Notification` | `mode` | `MA` | 新增 `LTB#auxiliaryFlows_keepDocumentedShellRoutes` |
| 12 | `showLoadingPreview` | `Flow/Settings` | `mode` | `MA` | 既有 `LST#loadingPreview_openAndClose` |
| 13 | `hideLoadingPreview` | `Flow/Settings` | `mode` | `MA` | 既有 `LST#loadingPreview_openAndClose` |
| 14 | `showIdle` | `Flow/Effect` | `mode`、`returnMode` | `MA` | 既有 `LST#showIdle_whenEnabledFromDrawer_entersIdleAndRemembersDrawer` |
| 15 | `hideIdle` | `Flow/Effect` | `mode` | `MA` | 既有 `LST#hideIdle_returnsToModeBeforeIdle` |
| 16 | `updateStatusBarMessage` | `Shell` | message 与 action 原子组 | `MA` | 既有 `LST#updateStatusBarMessage_trimsGlobalTransientMessage` |
| 17 | `updateStatusBarAction` | `Shell` | message 与 action 原子组 | `MA` | 新增 `LTB#statusBarAction_clearsMessageAndUpdatesAtomicActionFields` |

### 2.2 AppCatalog / Drawer（24）

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 18 | `showAppActionMenu` | `AppCatalog` | 应用菜单、搜索/滑轨焦点、编辑目标与草稿 | `MA` | 既有 `LST#appActionMenu_openedFromDrawerKeepsDrawerModeAndPrefillsSelectedApp` |
| 19 | `hideAppActionMenu` | `AppCatalog` | `isAppActionMenuVisible` | `MA` | 既有 `LST#appActionMenu_openedFromDrawerKeepsDrawerModeAndPrefillsSelectedApp` |
| 20 | `showAppManagement` | `Flow/AppCatalog` | `mode`、`returnMode`、编辑目标与草稿 | `MA` | 既有 `LST#appManagement_openedFromDrawerReturnsToDrawer` |
| 21 | `hideAppManagement` | `Flow/AppCatalog` | `mode` | `MA` | 既有 `LST#appManagement_openedFromDrawerReturnsToDrawer` |
| 22 | `moveAppEditorSelection` | `AppCatalog` | 编辑下标、姓名/别名草稿 | `MA` | 既有 `LST#appManagement_selectionWrapsAndSyncsDrafts` |
| 23 | `updateAppEditorNameDraft` | `AppCatalog` | `appEditorNameDraft` | `MA` | 既有 `LST#appManagement_updatesDraftsIndependently` |
| 24 | `updateAppEditorAliasDraft` | `AppCatalog` | `appEditorAliasDraft` | `MA` | 既有 `LST#appManagement_updatesDraftsIndependently` |
| 25 | `showAppDrawer` | `Flow/AppCatalog` | `mode`、可见应用、选择/窗口/页/焦点 | `MA` | 既有 `Journey#stateJourney_loadsAppsSearchesDrawerAndReturnsThroughSettings` |
| 26 | `withApps` | `AppCatalog` | 应用目录、可见应用、加载态、选择/窗口/页/焦点 | `MA` | 既有 `Journey#stateJourney_loadsAppsSearchesDrawerAndReturnsThroughSettings` |
| 27 | `moveSelection` | `AppCatalog` | 选择/窗口/页/焦点 | 无外部调用 | 既有 `LST#moveSelection_clampsAtLastIndex` |
| 28 | `scrollDrawerWindow` | `AppCatalog` | 选择/窗口/页/焦点 | 无外部调用 | 新增 `LTB#drawerViewportNavigation_keepsSelectionInsideWindow` |
| 29 | `pageSelection` | `AppCatalog` | 选择/窗口/页/焦点 | 无外部调用 | 新增 `LTB#drawerViewportNavigation_keepsSelectionInsideWindow` |
| 30 | `selectIndex` | `AppCatalog` | 选择/窗口/页/焦点 | `MA` | 新增 `LTB#drawerViewportNavigation_keepsSelectionInsideWindow` |
| 31 | `selectDrawerPage` | `AppCatalog` | 选择/窗口/页/焦点 | 无外部调用 | 新增 `LTB#drawerViewportNavigation_keepsSelectionInsideWindow` |
| 32 | `selectByPackageName` | `AppCatalog` | 命中时更新选择/窗口/页/焦点 | 无外部调用 | 新增 `LTB#drawerIdentityAndSearchExit_preserveFocusedApplication` |
| 33 | `selectByLetterIndex` | `AppCatalog` | 命中时更新选择/窗口/页/焦点 | 无外部调用 | 新增 `LTB#drawerIdentityAndSearchExit_preserveFocusedApplication` |
| 34 | `updateDrawerQuery` | `AppCatalog` | query、过滤列表、选择/窗口/页/焦点 | `MA` | 既有 `DQT#updateDrawerQuery_filtersToMatchingApps` |
| 35 | `appendDrawerQuery` | `AppCatalog` | 同 `updateDrawerQuery` | `MA` | 既有 `DQT#appendThenBackspaceDrawerQuery_roundTrips` |
| 36 | `backspaceDrawerQuery` | `AppCatalog` | 同 `updateDrawerQuery` | `MA` | 既有 `DQT#appendThenBackspaceDrawerQuery_roundTrips` |
| 37 | `clearDrawerQuery` | `AppCatalog` | query、默认排序列表、选择/窗口/页 | `MA` | 新增 `LTB#drawerIdentityAndSearchExit_preserveFocusedApplication` |
| 38 | `exitDrawerSearch` | `AppCatalog` | query、默认列表、当前应用、搜索/滑轨焦点、窗口 | `MA` | 新增 `LTB#drawerIdentityAndSearchExit_preserveFocusedApplication` |
| 39 | `reflowWindow` | `AppCatalog` | 选择/窗口/页/焦点 | `MA` | 既有 `LST#reflowWindow_clampsOutOfRangeSelectionToLastIndex` |
| 40 | `calculateListStartIndex` | `AppCatalog` | 无状态写入；返回窗口起点 | reducer 对象内 | 既有 `LST#calculateListStartIndex_selectionNearEndClampsToMaxStart` |
| 41 | `updateStats` | `AppCatalog` | recent、启动计数、最后启动包名 | `MA` | 新增 `LTB#homeAndSystemRefreshes_preserveIndependentSnapshots` |

### 2.3 Settings（8）

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 42 | `selectSettingsIndex` | `Settings` | 设置选择/窗口 | 无外部调用 | 新增 `LTB#settingsViewportNavigation_clampsAgainstDynamicRows` |
| 43 | `moveSettingsSelection` | `Settings` | 设置选择/窗口 | 无外部调用 | 新增 `LTB#settingsViewportNavigation_clampsAgainstDynamicRows` |
| 44 | `scrollSettingsWindow` | `Settings` | 设置选择/窗口 | 无外部调用 | 新增 `LTB#settingsViewportNavigation_clampsAgainstDynamicRows` |
| 45 | `reflowSettingsWindow` | `Settings` | 设置选择/窗口 | `MA` | 新增 `LTB#settingsViewportNavigation_clampsAgainstDynamicRows` |
| 46 | `updateAppearance` | `Settings` | 像素形状/尺寸/间隙、主题、归一化字体 | `MA` | 新增 `LTB#appearanceAndFontRefresh_normalizeSelectionAndKeepActivationSeparate` |
| 47 | `updateFontLoading` | `Settings` | `isFontLoading` | `MA` | 新增 `LTB#appearanceAndFontRefresh_normalizeSelectionAndKeepActivationSeparate` |
| 48 | `updateFontCacheSummary` | `Settings` | `fontCacheSummary` | `MA` | 新增 `LTB#appearanceAndFontRefresh_normalizeSelectionAndKeepActivationSeparate` |
| 49 | `updateUiBehavior` | `Flow/Settings-Effect` | Drawer 偏好、Idle 与 Pixel Matter 配置 | `MA` | 既有 `LST#updateUiBehaviorNormalizesIdleTimeoutSeconds` |

### 2.4 SMS（25）

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 50 | `showSmsRolePrompt` | `Flow/Sms` | `mode`、`returnMode` | `SMS-C` | 新增 `LTB#auxiliaryFlows_keepDocumentedShellRoutes` |
| 51 | `showSmsThreads` | `Flow/Sms` | `mode`、`returnMode`、页、会话选择/窗口 | `SMS-C` | 既有 `LST#showSmsThreads_keepsUnreadPageWhileLoadingMessages` |
| 52 | `hideSmsThreads` | `Flow/Sms` | `mode`、草稿/搜索/发送态、会话菜单 | `SMS-C` | 既有 `LST#hideSmsThreads_returnsHomeAndClearsDraftStatus` |
| 53 | `showSmsThreadDetail` | `Flow/Sms` | `mode`、`returnMode`、会话身份、消息/菜单/发送态 | `SMS-C` | 既有 `LST#showSmsThreadDetail_setsThreadIdentityAndReturnMode` |
| 54 | `showSmsMessageMenu` | `Sms` | 消息菜单可见性与 message id | `SMS-C` | 既有 `LST#showSmsMessageMenu_opensOnlyForMessageInCurrentConversation` |
| 55 | `hideSmsMessageMenu` | `Sms` | 消息菜单可见性与 message id | `SMS-C` | 既有 `LST#hideSmsMessageMenu_resetsMenuState` |
| 56 | `showSmsThreadMenu` | `Sms` | 会话菜单可见性与 conversation key | `SMS-C` | 既有 `LST#showSmsThreadMenu_opensOnlyForExistingThread` |
| 57 | `hideSmsThreadMenu` | `Sms` | 会话菜单可见性与 conversation key | `SMS-C` | 新增 `LTB#smsConversationRefresh_updatesIdentityWithoutClobberingDraft` |
| 58 | `updateSmsMutedConversations` | `Sms` | 静音会话键集合 | `SMS-C` | 既有 `LST#updateSmsMutedConversations_replacesMutedSet` |
| 59 | `hideSmsThreadDetail` | `Flow/Sms` | `mode`、`returnMode`、草稿/发送态、两类菜单 | `SMS-C` | 既有 `LST#hideSmsThreadDetail_returnsToThreadsAndKeepsModuleSearch` |
| 60 | `updateUnreadSmsEntries` | `Sms` | 未读消息、未读选择/窗口、页 | `SMS-C` | 既有 `LST#updateUnreadSmsEntries_switchesToAllWhenNoUnreadMessagesRemain` |
| 61 | `selectSmsIndex` | `Sms` | 未读选择/窗口 | 无外部调用 | 新增 `LTB#smsInboxViewportNavigation_clampsSelectionAndWindow` |
| 62 | `selectSmsPage` | `Sms` | 页、会话菜单 | `SMS-C` | 既有 `LST#selectSmsPage_dismissesThreadMenu` |
| 63 | `moveSmsSelection` | `Sms` | 未读选择/窗口 | `SMS-C` | 新增 `LTB#smsInboxViewportNavigation_clampsSelectionAndWindow` |
| 64 | `reflowSmsWindow` | `Sms` | 未读选择/窗口 | `MA` | 新增 `LTB#smsInboxViewportNavigation_clampsSelectionAndWindow` |
| 65 | `updateSmsCapability` | `Sms` | 默认短信角色、短信权限状态 | `SMS-C` | 新增 `LTB#smsConversationRefresh_updatesIdentityWithoutClobberingDraft` |
| 66 | `updateSmsThreads` | `Sms` | 会话列表、会话选择/窗口 | `SMS-C` | 新增 `LTB#smsThreadViewportNavigation_clampsSelectionAndWindow` |
| 67 | `selectSmsThreadIndex` | `Sms` | 会话选择/窗口 | 无外部调用 | 新增 `LTB#smsThreadViewportNavigation_clampsSelectionAndWindow` |
| 68 | `moveSmsThreadSelection` | `Sms` | 会话选择/窗口 | `SMS-C` | 新增 `LTB#smsThreadViewportNavigation_clampsSelectionAndWindow` |
| 69 | `reflowSmsThreadWindow` | `Sms` | 会话选择/窗口 | `MA` | 新增 `LTB#smsThreadViewportNavigation_clampsSelectionAndWindow` |
| 70 | `updateSmsMessages` | `Sms` | 当前会话身份与详情消息 | `SMS-C` | 新增 `LTB#smsConversationRefresh_updatesIdentityWithoutClobberingDraft` |
| 71 | `updateSmsAllMessages` | `Sms` | 全量消息 | `SMS-C` | 新增 `LTB#smsConversationRefresh_updatesIdentityWithoutClobberingDraft` |
| 72 | `updateSmsDraftText` | `Sms` | 会话草稿 | `SMS-C` | 新增 `LTB#smsConversationRefresh_updatesIdentityWithoutClobberingDraft` |
| 73 | `updateSmsThreadSearchQuery` | `Sms` | 搜索词、会话选择/窗口 | `SMS-C` | 既有 `LST#updateSmsThreadSearchQuery_clampsLongInput` |
| 74 | `updateSmsSendStatus` | `Sms` | 发送状态 | `SMS-C` | 既有 `LST#updateSmsSendStatus_updatesOnlyDraftStatus` |

### 2.5 Phone / Contacts（15）

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 75 | `showCallLog` | `Flow/Phone` | `mode`、`returnMode`、通话页 | `CALL` | 既有 `LST#showCallLog_landsOnDialPadWhenCallLogUnreadable` |
| 76 | `hideCallLog` | `Flow/Phone` | `mode`、拨号输入/匹配 | `CALL` | 既有 `LST#hideCallLog_clearsDialInput` |
| 77 | `selectCallPage` | `Phone` | 通话页 | `CALL` | 既有 `LST#selectCallPage_clampsToValidRange` |
| 78 | `updateDialInput` | `Phone` | 拨号输入、清空旧匹配 | `CALL` | 既有 `LST#updateDialInput_clearsStaleMatches` |
| 79 | `updateDialMatches` | `Phone` | 输入仍一致时回填匹配 | `CALL` | 既有 `LST#updateDialMatches_ignoresResultForStaleInput` |
| 80 | `updateCallLogGroups` | `Phone` | 通话记录、加载态 | `CALL` | 新增 `LTB#phoneAndContactsRefresh_landDataWithoutMixingDrafts` |
| 81 | `showContactDetail` | `Flow/Phone` | `mode`、详情 lookup key | `CONTACTS` | 既有 `LST#showContactDetail_requiresLookupKeyAndHideReturnsToContactsPage` |
| 82 | `hideContactDetail` | `Flow/Phone` | `mode`、通话页、详情 lookup key | `CONTACTS` | 既有 `LST#showContactDetail_requiresLookupKeyAndHideReturnsToContactsPage` |
| 83 | `showContactEditor` | `Flow/Phone` | `mode`、编辑 lookup key 与草稿 | `CONTACTS` | 既有 `LST#contactEditor_openPrefillsNameAndCloseReturnsByOrigin` |
| 84 | `hideContactEditor` | `Flow/Phone` | `mode`、通话页、详情/编辑 key 与草稿 | `CONTACTS` | 既有 `LST#contactEditor_openPrefillsNameAndCloseReturnsByOrigin` |
| 85 | `updateContactEditorName` | `Phone` | 联系人姓名草稿 | `CONTACTS` | 新增 `LTB#phoneAndContactsRefresh_landDataWithoutMixingDrafts` |
| 86 | `updateContactEditorNumber` | `Phone` | 联系人号码草稿 | `CONTACTS` | 新增 `LTB#phoneAndContactsRefresh_landDataWithoutMixingDrafts` |
| 87 | `beginContactsLoading` | `Phone` | 联系人加载态 | `CONTACTS` | 既有 `LST#updateContacts_landsDataPermissionAndClearsLoading` |
| 88 | `updateContacts` | `Phone` | 联系人、加载态、联系人权限 | `CONTACTS` | 既有 `LST#updateContacts_landsDataPermissionAndClearsLoading` |
| 89 | `updateCallCapability` | `Flow/Phone-System` | 通话权限、通话记录权限 | `CALL` | 既有 `LST#updateCallCapability_tracksBothPermissionsIndependently` |

### 2.6 Effect（1）

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 90 | `recordInteraction` | `Effect` | `lastInteractionUptimeMs` | `MA` | 新增 `LTB#homeAndSystemRefreshes_preserveIndependentSnapshots` |

### 2.7 Home（6）

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 91 | `updateTime` | `Home` | 时间、日期、星期 | `MA` | 新增 `LTB#homeAndSystemRefreshes_preserveIndependentSnapshots` |
| 92 | `updateNextAlarmText` | `Home` | 下次闹钟 | `MA` | 新增 `LTB#homeAndSystemRefreshes_preserveIndependentSnapshots` |
| 93 | `updateCommunicationStatus` | `Home` | 未接来电数、未读短信数 | `MA` | 新增 `LTB#homeAndSystemRefreshes_preserveIndependentSnapshots` |
| 94 | `updateMediaPlayback` | `Home` | 媒体播放快照 | `MA` | 新增 `LTB#homeAndSystemRefreshes_preserveIndependentSnapshots` |
| 95 | `updateRainHintText` | `Home` | 天气摘要、刷新时间 | `MA` | 既有 `LST#updateRainHintText_updatesSummaryAndRefreshTime` |
| 96 | `updateScreenUsageSummary` | `Home` | 屏幕使用时长、亮屏次数 | `MA` | 新增 `LTB#homeAndSystemRefreshes_preserveIndependentSnapshots` |

### 2.8 Notification（2）

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 97 | `updateNotificationSummary` | `Notification` | 摘要文本、数量、来源与通知项 | `MA` | 既有 `LST#updateNotificationSummary_trimsSummaryAndClampsCount` |
| 98 | `updateNotificationRules` | `Notification` | 静音/优先来源集合 | `MA` | 既有 `LST#updateNotificationRulesTrimsSourcesAndLetsMuteWin` |

### 2.9 System（2）

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 99 | `updateDeviceStatus` | `System` | 电量、充电态 | `MA` | 新增 `LTB#homeAndSystemRefreshes_preserveIndependentSnapshots` |
| 100 | `updateDataHealth` | `System` | 六项平台能力、健康刷新时间 | `MA` | 既有 `LST#updateDataHealthPreservesOrWritesRefreshTime` |

### 2.10 reducer 外既有写入的具名转换（8）

这 8 个入口均已接入生产调用：`SmsController` 与 `CallController` 的 4 个入口消除了
Controller 内 5 个直接聚合 `copy`，`MainActivity` 的 4 个入口消除了 7 个可达表达式。
剩余 1 个 Pager 死 fallback 已直接删除，当前 copy baseline 为空，零容忍门禁生效。

| # | 方法 | 目标领域 | 主要写入字段 | 生产调用 | 直接行为测试证据 |
|---:|---|---|---|---|---|
| 101 | `dismissDrawerOverlaysForPagerDrag` | `AppCatalog` | Drawer 搜索焦点、应用菜单 | `MA#onMainPageDragStart` | 新增 `LSW#dismissDrawerOverlaysForPagerDrag_closesOnlyFocusAndMenu` |
| 102 | `prepareDrawerEntryFocus` | `AppCatalog` | Drawer 搜索焦点、Rail 滑动态 | `MA#onMainPageChanged/showAppDrawer` | 新增 `LSW#prepareDrawerEntryFocus_updatesOnlyEntryFocusAndRail` |
| 103 | `focusDrawerSearchInput` | `AppCatalog` | Drawer 搜索焦点、Rail 滑动态 | `MA#onPixelEngineDrawerQueryChanged/handleDrawerTextInput` | 新增 `LSW#focusDrawerSearchInput_requiresVisibleDrawerAndPreservesOtherFields` |
| 104 | `beginAppCatalogLoading` | `AppCatalog` | 应用目录 loading | `MA#loadApps` | 新增 `LSW#beginAppCatalogLoading_updatesOnlyCatalogLoadingFlag` |
| 105 | `finishSmsThreadsLoading` | `Sms` | 短信会话 loading | `SMS-C#openModule/applySmsData`（已迁移） | 新增 `LSW#finishSmsThreadsLoading_updatesOnlySmsLoadingFlag` |
| 106 | `beginForcedSmsRefresh` | `Sms` | 清空三份 provider 快照、开始 loading | `SMS-C#openModule`（已迁移） | 新增 `LSW#beginForcedSmsRefresh_resetsProviderSnapshotsAndPreservesSessionState` |
| 107 | `moveSmsSearchSelection` | `Sms` | 搜索结果选择下标 | `SMS-C#moveThreadSelection`（已迁移） | 新增 `LSW#moveSmsSearchSelection_clampsAgainstResultCountAndRequiresQuery` |
| 108 | `prepareCallLogLoading` | `Phone` | 通话记录 loading | `CALL#openCallLog`（已迁移） | 新增 `LSW#prepareCallLogLoading_requiresPermissionAndEmptyCache` |

历史上剩余的 1 个 baseline 表达式是 `MainActivity.onMainPageChanged()` 的 `else -> state.copy(mode = mode)`。
`LauncherRootHost.MAIN_PAGE_MODES` 只会发出 `SETTINGS`、`HOME`、`APP_DRAWER`，三者均已被显式分支处理，
因此该 `else` 对真实 Pager 回调不可达。当前已直接收窄为 `else -> state`，没有为死路径新增 transition，
也没有引入可写任意路由的 generic mode setter；它不再计入 baseline。

全部表格恰好包含 108 个唯一入口；跨切片职责已经直接写入每个入口的 `Flow/...` 目标领域，不重复列行。

## 3. 机器复核

以下命令从生产源码提取 108 个方法，再与所有 JVM 测试中的真实调用求差集：

```bash
SOURCE_METHODS="$(mktemp)"
TEST_METHODS="$(mktemp)"

rg -o '^\s*(public\s+)?fun\s+[A-Za-z_][A-Za-z0-9_]*' \
  app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherStateTransitions.kt \
  | sed -E 's/.*fun[[:space:]]+//' \
  | sort -u > "$SOURCE_METHODS"

rg -o 'LauncherStateTransitions\.[A-Za-z_][A-Za-z0-9_]*' app/src/test/java \
  | sed 's/.*\.//' \
  | sort -u > "$TEST_METHODS"

wc -l "$SOURCE_METHODS"                         # 108
comm -12 "$SOURCE_METHODS" "$TEST_METHODS" | wc -l  # 108
comm -23 "$SOURCE_METHODS" "$TEST_METHODS"          # 无输出
```

阶段 0 前的 56 个直接覆盖可通过排除两轮新增测试复核：

```bash
rg -o 'LauncherStateTransitions\.[A-Za-z_][A-Za-z0-9_]*' app/src/test/java \
  --glob '!LauncherTransitionBaselineTest.kt' \
  --glob '!LauncherStateWriteTransitionsTest.kt' \
  | sed 's/.*\.//' \
  | sort -u > "$TEST_METHODS"
comm -12 "$SOURCE_METHODS" "$TEST_METHODS" | wc -l  # 56
```

文档表格的 108 行应与源码集合一一对应：

```bash
DOC_METHODS="$(mktemp)"
rg -o '^\| [0-9]+ \| `[A-Za-z_][A-Za-z0-9_]*` \\|' \
  docs/testing/launcher-transition-baseline.md \
  | sed -E 's/.*`([A-Za-z_][A-Za-z0-9_]*)`.*/\1/' \
  | sort -u > "$DOC_METHODS"

wc -l "$DOC_METHODS"                              # 108
comm -3 "$SOURCE_METHODS" "$DOC_METHODS"           # 无输出
```

## 4. 当前没有外部生产调用的入口

以下 11 个旧公开入口在 reducer 对象外没有生产引用，但都有实际行为基线。它们可能是已被 UI 交互路径替代的遗留 API，
也可能是下一轮输入统一化需要的候选；在确认调用意图前不应直接删除：

1. `moveSelection`
2. `scrollDrawerWindow`
3. `pageSelection`
4. `selectDrawerPage`
5. `selectByPackageName`
6. `selectByLetterIndex`
7. `selectSettingsIndex`
8. `moveSettingsSelection`
9. `scrollSettingsWindow`
10. `selectSmsIndex`
11. `selectSmsThreadIndex`

`calculateListStartIndex` 也没有对象外引用，但它被 reducer 内部窗口同步逻辑调用，因此不列入外部孤儿 API。

这 8 个具名入口均已有外部生产调用：Controller 对应入口由 `SmsController` / `CallController`
调用，MainActivity 对应入口由 `loadApps`、`onMainPageChanged`、`onMainPageDragStart`、
`showAppDrawer`、`onPixelEngineDrawerQueryChanged` 与 `handleDrawerTextInput` 调用。
Pager 死写入已按上节策略直接删除，copy guard 当前为空基线。

## 5. `SNAKE` 重复分支调查

`MainActivity.onKeyDown()` 的 Enter / DPAD Center 分支中，同一个 `when (state.mode)` 目前包含：

```kotlin
LauncherMode.CONTACT_DETAIL -> Unit
LauncherMode.CONTACT_EDITOR -> Unit
LauncherMode.SNAKE -> Unit
LauncherMode.SMS_THREAD_DETAIL -> {
    // ...
}
// ...
LauncherMode.DIAGNOSTICS -> closeDiagnostics()
LauncherMode.SNAKE -> closeSnake()
```

事实结论：

- 第一个 `LauncherMode.SNAKE -> Unit` 先匹配，当前 Enter 行为是 **no-op**。
- 后面的 `LauncherMode.SNAKE -> closeSnake()` 永远不可达，Kotlin 编译器会报告
  `Duplicate branch condition in 'when'`。
- 两个分支均由提交 `9910d3c9` 引入，无法仅靠提交先后判断哪一个是最终意图。
- 同一提交在系统 Back 路径中明确使用 `LauncherMode.SNAKE -> closeSnake()`；方向键用于游戏转向，
  提交说明还明确“点按场地重开”，没有把 Enter 描述为退出键。

因此本基线只记录：**当前行为是 Enter no-op，后分支不可达，产品意图待确认**。后续生产修复任务应先决定：
保留 no-op 并删除死分支，还是让 Enter 退出并替换前一个分支；本任务不修改 `MainActivity`。

## 6. 仍需后续任务处理的风险

- `updateSmsMessages` 会原子写入会话身份和消息，并保留草稿/发送态；但 reducer 自身不拒绝旧会话结果。
  当前 `SmsController.applySmsData()` 在主线程读取“当下会话”后组装参数，调用方仍承担新鲜度责任。
  既有 Controller 宿主行为测试已覆盖发送结果晚到后切换会话的场景；provider 刷新、销毁与快速切换
  的组合仍需要更高层运行时测试，不能由纯 reducer 测试证明。
- `showContactDetail` 的注释说“仅允许从拨号模块进入”，实现只校验 `lookupKey` 非空，没有检查来源 mode。
  这是文档与行为差异，拆分前需要产品/架构决策，不能在基线任务里擅自改变。
- 11 个外部孤儿 API 会扩大拆分后的公共写入面。应在输入路径盘点后逐个决定接回、降为私有或删除。
- 108/108 表示 reducer 入口有直接 JVM 行为证据，不表示 `MainActivity`、Controller、线程调度、
  Android 生命周期和真实输入分发已经具备端到端覆盖。
