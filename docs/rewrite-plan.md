# PixelLauncher → pixel-engine 全量重写计划

> 状态：草稿 · 2026-05-21
> 原则：**保留全部 UI 设计**，把渲染/交互/状态层全部迁移到 pixel-engine；IDLE 流体物理完全丢弃，留待日后重新实现。

---

## 一、现状摘要

### 旧渲染体系

| 组件 | 说明 |
|---|---|
| `PixelBuffer` (ByteArray) | 三态像素：OFF=0 / ON=1 / ACCENT=2 |
| `PixelPalette` | 把三态映射到 Android Color（5 种主题） |
| `PixelRenderer` (1950 行) | 命令式逐像素绘制，覆盖全部 9 个屏幕 |
| `PixelFontEngine` + `GlyphStyle` | 独立字形引擎，UI_SMALL_10 / APP_LABEL_16 |
| `PixelDisplayView` / `PixelGlDisplayView` | Canvas 和 OpenGL 双渲染路径 |
| `MainActivity` (4421 行) | 渲染调度 + 状态机 + 全部交互路由 |

### 9 个 LauncherMode

```
HOME / APP_DRAWER / SETTINGS
SMS_ROLE_PROMPT / SMS_THREADS / SMS_THREAD_DETAIL / SMS_INBOX
DIAGNOSTICS / IDLE
```

---

## 二、目标架构

```
app/
├── ui/
│   ├── theme/          LauncherTheme（5 套 PixelColor 颜色方案）
│   ├── widget/         跨屏复用组件（LauncherHeader、BatteryDivider）
│   ├── screen/         每个 Mode 对应一个 Widget 构建函数
│   └── LauncherRoot    顶层 Pager + SMS 覆盖层
├── viewmodel/
│   └── LauncherViewModel   单一 StateFlow<LauncherUiState>
├── data/               ← 原样保留，不动
└── app/MainActivity    ← 大幅瘦身，只做生命周期和数据接线
```

**核心原则**：
- `PixelHostView.setContent { LauncherRoot(uiState) }` 是唯一渲染入口
- 所有屏幕都是 `StatefulWidget` 或纯 `Widget` 构建函数，没有任何手工坐标计算
- 状态从 `LauncherViewModel` 单向流入 widget 树；用户动作通过 lambda 回调出来
- `LauncherStateTransitions.*` 纯函数保留，由 ViewModel 调用

---

## 三、颜色主题体系

旧 `PixelPalette` 的四色（background / pixelOn / pixelOff / accent）对应到 pixel-engine 三层：

| 旧字段 | pixel-engine 落点 |
|---|---|
| `backgroundColor` | `PixelHostView.backgroundColor` |
| `pixelOffColor` | `PixelHostView.pixelGridColor`（死格底色） |
| `pixelOnColor` | widget 默认前景色（TextStyle / Container） |
| `accentColor` | app 层 `LauncherTheme.accentColor`（橙色位置） |

新文件：`ui/theme/LauncherTheme.kt`

```kotlin
data class LauncherTheme(
    val backgroundColor: PixelColor,
    val pixelGridColor: PixelColor,
    val primaryColor: PixelColor,
    val accentColor: PixelColor,
    val dimColor: PixelColor,          // 用于次要文字
)

object LauncherThemes {
    val GREEN_PHOSPHOR = LauncherTheme(
        backgroundColor = PixelColor.fromRgb(0,    0,    0  ),
        pixelGridColor  = PixelColor.fromRgb(8,    37,   13 ),
        primaryColor    = PixelColor.fromRgb(151,  255,  167),
        accentColor     = PixelColor.fromRgb(199,  255,  208),
        dimColor        = PixelColor.fromRgb(80,   160,  90 ),
    )
    val AMBER_CRT = LauncherTheme(...)
    val ICE_LCD   = LauncherTheme(...)
    val MONO_LCD  = LauncherTheme(...)
    val NIGHT_MONO= LauncherTheme(...)
}
```

主题变更时调用：
```kotlin
hostView.backgroundColor = theme.backgroundColor
hostView.pixelGridColor  = theme.pixelGridColor
```

---

## 四、共享 UI 组件

### 4.1 LauncherHeader

出现在所有屏幕顶部。规格来自旧 `LauncherHeaderLayout`：

```
行高  = UI_SMALL_10.cellHeight（即 font 高度）
左侧  = 当前时间文字（HH:MM）
右侧  = 屏幕标题（HOME / APP DRAWER / SETTINGS 等）
分隔线 = 下一行：一像素高全宽横线，左段填充 = 电池百分比
充电指示 = 分隔线上某像素以 accentColor 闪烁
```

实现方式：`BatteryDivider` 做成 `PixelLeafRenderObjectWidget`，直接用
`context.buffer.setPixel()` 绘制一行像素；外层用 `Column` + `Row` 组合时间/标题文字。

### 4.2 BatteryDivider

```kotlin
// 伪代码
class BatteryDividerWidget(
    val batteryLevel: Int,   // 0–100
    val isCharging: Boolean,
    val chargeTick: Int,     // 动画帧计数，用于闪烁位置
    val primaryColor: PixelColor,
    val accentColor: PixelColor,
) : PixelLeafRenderObjectWidget(...)
```

paint 逻辑：
1. 整行画空（`pixelGridColor` 已作底）
2. `0 .. width * batteryLevel / 100` 范围填 `primaryColor`
3. 若充电中：在 `filledEndX + (chargeTick % 3)` 位置设 `accentColor`

---

## 五、屏幕迁移规范

### 5.1 HOME

**数据来源**（从 ViewModel 读取）：
- `currentTimeText`、`currentDateText`
- `rainHintText`、`nextAlarmText`、`missedCallCount`、`unreadSmsCount`
- `screenUsageTimeText`、`screenOpenCountText`

**Widget 结构**：
```
Column(STRETCH) {
    LauncherHeader(time, "HOME", battery)
    Padding {
        Text(currentDateText)
        Spacer(2)
        Text(rainHintText)
        Text("ALARM $nextAlarmText")        // 仅当有闹钟时
        Text("CALL $missed  SMS $unread")   // 仅当有未读时
        Text("USE $usage  OPEN $opens")
    }
    Spacer(Expanded)
    Row(STRETCH) {
        OutlinedButton("CONTACT", accentColor, onTap=openContacts)
        Spacer(Expanded)
        OutlinedButton("SMS", accentColor, onTap=openSms)
    }
}
```

**交互**（通过 PixelHostView 的 PixelSystemAction 或直接 callback）：
- 点击 CONTACT → 打开通讯录 Intent
- 点击 SMS → 进入 SMS_THREADS

### 5.2 APP_DRAWER

**现状**：`PixelEngineDrawerHost` 已 90% 完成，功能上对齐旧渲染。

**待补全**：
- `pixelGapRatio` 替换 `setPixelGapEnabled`（已完成）
- 主题颜色接入（主色/强调色从 `LauncherTheme` 注入，而不是硬编码）
- `DrawerListAlignment`（LEFT/CENTER/RIGHT）在列表项文字对齐上体现

### 5.3 SETTINGS

**数据来源**：`LauncherState` 中的各项偏好设置字段

**Widget 结构**：`ListViewBuilder` 逐行渲染 `SettingsMenuRow`
- 每行：`Row { Text(title, primary); Spacer(Expanded); Text("<value>", dim) }`
- 选中行：border 高亮（`accentColor`）
- 左右箭头触发 `SettingsMenuModel.nextXxx()` → 更新 ViewModel

**需保留的 SettingsMenuItem**（去掉 `IDLE_PAGE`、`CHARGE_EFFECT`）：
```
FONT_SIZE / FONT_STYLE / RESOLUTION / PIXEL_GAP
STYLE / THEME / APP_LIST_ALIGNMENT / DRAWER_AUTO_SEARCH
```

### 5.4 SMS_ROLE_PROMPT

静态内容，`Column` 居中 4 行文字 + 一个 `OutlinedButton("CONTINUE")` 触发角色申请。

### 5.5 SMS_THREADS

`ListViewBuilder` 每行显示：
- 第一行：发件人地址（左，primary）+ 时间戳（右，accent）
- 第二行：消息摘要（dim）

点击某行 → `showSmsThreadDetail`

### 5.6 SMS_THREAD_DETAIL

```
Column(STRETCH) {
    LauncherHeader(time, "SMS", battery)
    ListViewBuilder(messages) { msg ->
        Column {
            Row { Text(IN/OUT, accent); Spacer; Text(time, dim) }
            Text(body, primary)
        }
    }
    // 底部固定区域
    Row {
        Expanded { TextField(draft, placeholder="TYPE MSG") }
        OutlinedButton("SEND", onTap=sendDraft)
    }
}
```

### 5.7 SMS_INBOX

横向 `Pager`（一条消息一页），每页：
- Header：发件人 + 当前/总数（如 "2/5"）
- 正文：`SingleChildScrollView { Text(body) }`

左右滑动切换未读消息；点击正文区域打开完整会话。

### 5.8 DIAGNOSTICS

`ListViewBuilder` 纯文本行，数据来自 `DiagnosticsModel`。

### 5.9 IDLE（简化占位版）

**不再有流体物理**。替换为：
```
Column(STRETCH, CENTER) {
    Text(currentTimeText, style=big)  // 将来可换成像素大字体
    Text(currentDateText, style=dim)
}
```
充电动画、流体粒子留空，未来单独立项实现。

---

## 六、导航设计

### 主 Pager（横滑三页）

```
页 0: HOME
页 1: APP_DRAWER
页 2: SETTINGS
```

用 pixel-engine 的 `Pager` widget，`PagerController` 由 ViewModel 控制当前页。

### SMS / IDLE 覆盖层

SMS 四个屏幕和 IDLE 不进 Pager，而是通过 ViewModel 中的 `overlayMode` 字段控制：

```kotlin
// LauncherRoot 伪代码
fun LauncherRoot(state: LauncherUiState): Widget {
    val overlay = state.overlayMode
    if (overlay != null) {
        return OverlayScreen(overlay, state)  // SMS/IDLE/DIAGNOSTICS
    }
    return MainPager(state)
}
```

`overlayMode` 非空时 Pager 不渲染（节省帧预算）。

### 手势和按键

| 动作 | 行为 |
|---|---|
| 主 Pager 左右滑 | 页面切换 |
| HOME 上滑 | 跳转 APP_DRAWER（页 1） |
| 任意覆盖层 Back | 关闭覆盖层，回到主 Pager |
| 硬件音量键（如有） | 待定 |
| D-Pad ↑↓ | SETTINGS / SMS 列表选择 |
| D-Pad ←→ | SETTINGS 值循环 |
| D-Pad 确认 | 激活选中项 |

---

## 七、文件处置清单

### 7.1 完全删除（~3500 行）

```
render/PixelBuffer.kt           → 替换为 pixelcore.PixelBuffer
render/PixelPalette.kt          → 替换为 ui/theme/LauncherTheme.kt
render/PixelRenderer.kt         → 替换为各 Screen widget 树
render/PixelFontEngine.kt       → 替换为 pixelcore 字体引擎
render/PixelFontCatalog.kt      → 替换为 pixelcore PixelBitmapFont
render/PixelGlyphPack.kt
render/PixelFrameView.kt        → 替换为 pixelcore.PixelFrameView
render/PixelDisplayView.kt      → 替换为 pixelui.PixelHostView
render/PixelGlDisplayView.kt    → GL 路径废弃
render/PixelGlRenderer.kt
render/ScreenProfile.kt         → 替换为 pixelcore.ScreenProfile
render/ScreenProfileFactory.kt  → 替换为 pixelcore.ScreenProfileFactory
render/PixelGridGeometry.kt     → 替换为 pixelcore.PixelGridGeometryResolver
render/FrameSwapBuffer.kt
render/HorizontalPageView.kt    → 替换为 pixelui Pager
render/HeaderBatteryIndicator.kt→ 替换为 BatteryDivider widget
render/LauncherAnimationState.kt→ 大部分动画状态随旧渲染一起删除
render/RenderPerfLogger.kt      → 可选保留

render/IdleFluidEngine.kt       ← IDLE 丢弃
render/IdleFluidTuning.kt
render/IdleMaskFrame.kt
render/IdleSimulationProfile.kt
render/ChargeIdleEffectRenderer.kt
render/ChargeIdleEffectRegistry.kt
render/ChargeIdleEffect.kt（enum）
render/charge/*.kt（6 个文件）

launcher/HomeLayout.kt          → 布局数学 → widget 约束
launcher/AppListLayout.kt
launcher/SettingsMenuLayout.kt
launcher/SmsLayout.kt
launcher/DrawerRailDragMapper.kt
launcher/DrawerVerticalScrollController.kt
launcher/DrawerMotionInterruption.kt
launcher/DrawerDirectionalSettlePolicy.kt
launcher/TextListSupport.kt
launcher/DrawerSearchSupport.kt
launcher/DrawerContentTapResolver.kt
launcher/AppDrawerIndexModel.kt
launcher/DrawerAlphaIndexModel.kt
```

### 7.2 保留不动

```
data/AppRepository.kt
data/CommunicationStatusRepository.kt
data/DeviceLocationRepository.kt
data/DeviceMotionRepository.kt（暂保留，IDLE 重新实现时可能用）
data/DeviceStatusRepository.kt
data/FontSettingsRepository.kt
data/LauncherStatsRepository.kt
data/NextAlarmRepository.kt
data/PackageManagerAppRepository.kt
data/RainForecastRepository.kt
data/ScreenUsageRepository.kt
data/SmsRepository.kt / UnreadSmsRepository.kt / SmsNotificationHelper.kt
app/MmsDeliverReceiver.kt / SmsDeliverReceiver.kt / RespondViaMessageService.kt
app/PixelLauncherApp.kt
system/AndroidAppLauncher.kt
system/ScreenGravityMapper.kt（IDLE 重新实现时使用）
system/WindowModeController.kt
util/*.kt
```

### 7.3 保留并改写

```
app/MainActivity.kt     → 瘦身至 ~200 行（生命周期 + 权限 + 数据接线）
launcher/LauncherState.kt         → 简化，删除 IDLE 相关字段
launcher/LauncherStateTransitions.kt → 保留大部分纯函数，删除 IDLE/FluidState 相关
launcher/SettingsMenuModel.kt     → 删除 IDLE/CHARGE 选项
launcher/HomeFixedInfoModel.kt    → 保留
launcher/AppEntry.kt              → 保留
launcher/SettingsMenuModel.kt     → 保留（去掉 IDLE_PAGE/CHARGE_EFFECT）
launcher/LauncherStateTransitions.kt → 保留，由 ViewModel 调用
```

### 7.4 新建文件

```
ui/theme/LauncherTheme.kt
ui/widget/LauncherHeader.kt
ui/widget/BatteryDividerWidget.kt
ui/screen/HomeScreen.kt
ui/screen/DrawerScreen.kt（原 PixelEngineDrawerHost 迁移/整理）
ui/screen/SettingsScreen.kt
ui/screen/SmsRolePromptScreen.kt
ui/screen/SmsThreadsScreen.kt
ui/screen/SmsThreadDetailScreen.kt
ui/screen/SmsInboxScreen.kt
ui/screen/DiagnosticsScreen.kt
ui/screen/IdleScreen.kt（占位版）
ui/LauncherRoot.kt
viewmodel/LauncherViewModel.kt
viewmodel/LauncherUiState.kt
```

---

## 八、分阶段执行计划

> 每个 Phase 结束后可独立构建和运行，不影响线上可用性。
> Phase 1–2 完成前旧渲染和新渲染并行存在。

---

### Phase 0 · 架构搭底（不改任何现有渲染）

**目标**：新的 ViewModel 层和数据流接好，旧 MainActivity 继续工作

1. 新建 `LauncherUiState`（从 `LauncherState` 剥离 IDLE/流体字段）
2. 新建 `LauncherViewModel`：持有 `StateFlow<LauncherUiState>`，汇聚所有 Repository 数据流
3. `MainActivity` 订阅 ViewModel，把 `uiState` 传给旧渲染路径（先不改渲染）
4. 确保旧功能全部正常

**验收**：应用完全正常，ViewModel 输出的 state 与旧 `state` 值一致

---

### Phase 1 · 主题体系

**目标**：`PixelHostView` 的 `backgroundColor` + `pixelGridColor` 跟随主题切换

1. 新建 `ui/theme/LauncherTheme.kt`（5 套颜色方案）
2. ViewModel 暴露 `currentTheme: LauncherTheme`
3. `MainActivity` 在主题变更时调用：
   ```kotlin
   hostView.backgroundColor = theme.backgroundColor
   hostView.pixelGridColor  = theme.pixelGridColor
   ```
4. 现阶段 PixelHostView 里跑的仍是旧渲染，但背景色已走新路径

**验收**：切换主题时背景色和像素格底色正确变化

---

### Phase 2 · 共享 Header 组件

**目标**：`LauncherHeader` + `BatteryDivider` 可独立渲染验证

1. 实现 `BatteryDividerWidget`（`PixelLeafRenderObjectWidget`）
2. 实现 `LauncherHeader`（Row：时间文字 + 标题文字 + BatteryDivider）
3. 在 pixel-demo 中新建一个 `HeaderPreviewScene` 验证视觉效果

**验收**：Demo 中 Header 与旧截图视觉一致（电量、充电动画、标题对齐）

---

### Phase 3 · SETTINGS 屏幕

**目标**：SETTINGS 完全用 pixel-engine 渲染，替换 `drawSettings`

1. 实现 `SettingsScreen.kt`（`ListViewBuilder` + 行内高亮 + 左右交互）
2. 连接到 ViewModel：读取设置值，写回 `settingsSelectedIndex`、`selectedXxx`
3. `LauncherRoot` 占位实现：当 `overlayMode == SETTINGS`（实际上 SETTINGS 是 Pager 的第 3 页，先单独走覆盖层路径方便调试）
4. 在真机 SETTINGS 页切换到新渲染，其他页保持旧渲染

**验收**：SETTINGS 页完全可用，字体/像素大小/主题/对齐设置生效

---

### Phase 4 · HOME 屏幕

**目标**：HOME 页用 pixel-engine 渲染

1. 实现 `HomeScreen.kt`
2. 数据从 ViewModel 读取（时间、日期、天气、闹钟、通信状态、使用统计）
3. CONTACT / SMS 按钮通过 `PixelSystemAction` 或直接 callback 触发系统 Intent
4. 切换 HOME 页到新渲染

**验收**：HOME 页视觉与旧版一致，按钮可用

---

### Phase 5 · APP_DRAWER 完成

**目标**：`PixelEngineDrawerHost` 全功能对齐，主题颜色接入

1. 主色/强调色从 `LauncherTheme` 注入（替换硬编码橙色）
2. `DrawerListAlignment` 在 ListViewBuilder 的 `crossAxisAlignment` 上体现
3. 删除 `PixelEngineDrawerHost` 中对 `setPixelGapEnabled` 的调用，改用 `setPixelGapRatio`
4. 确保 Pager 联动：从 HOME 滑到 DRAWER 流畅

**验收**：DRAWER 功能与旧版一致，主题颜色正确

---

### Phase 6 · SMS 屏幕（4 个）

按难度升序：

**6.1 SMS_ROLE_PROMPT**：静态文字 + 按钮，半小时实现

**6.2 SMS_THREADS**：`ListViewBuilder`，每行双行布局（sender + time + snippet）

**6.3 SMS_INBOX**：横向 `Pager`（每页一条未读消息），纵向 `SingleChildScrollView` 滚动正文

**6.4 SMS_THREAD_DETAIL**（最复杂）：
- 消息列表（`ListViewBuilder`，IN/OUT 区分对齐）
- 底部 `TextField` 草稿输入
- SEND 按钮触发短信发送

每个 SMS 屏幕完成后切换到新渲染，并行期间旧渲染保留。

**验收**：四个 SMS 屏幕完全可用，包括发送功能

---

### Phase 7 · DIAGNOSTICS + IDLE 占位

**7.1 DIAGNOSTICS**：`ListViewBuilder` 纯文本，从 `DiagnosticsModel` 取数据

**7.2 IDLE（占位版）**：
- 删除 `LauncherMode.IDLE` 相关的流体渲染路径
- 简单居中显示 `currentTimeText`（大字体）
- 充电时进入 IDLE 展示此简单页面
- 在 `LauncherState` / `SettingsMenuModel` 中删除 `chargeIdleEffect` 字段

**验收**：IDLE 不再崩溃，显示简单时钟

---

### Phase 8 · LauncherRoot + 统一导航

**目标**：一个 `LauncherRoot` widget 统一管理所有屏幕切换

1. 实现 `LauncherRoot.kt`：
   - 主 Pager（HOME / DRAWER / SETTINGS）
   - `overlayMode` 非空时渲染对应 Screen，盖住 Pager
2. `PixelHostView.setContent { LauncherRoot(uiState) }` 成为唯一渲染调用
3. 手势路由：上滑 HOME → 跳 DRAWER 页；Back 关闭覆盖层
4. 硬件 D-Pad 路由通过 `PixelHostBridge.dispatchSystemAction` 传入

**验收**：全部 9 个屏幕（IDLE 占位）都能从 LauncherRoot 访问

---

### Phase 9 · MainActivity 瘦身

**目标**：删除所有旧渲染代码，MainActivity 只剩生命周期接线

1. 删除 `pixelRenderer`、`pixelFrameView`、`pixelBuffer`、所有旧 render 相关字段
2. 删除手工坐标命中测试（`onLogicalTap`、`onLogicalDragStart` 等全部移入 widget 层）
3. 删除 `animationState`、`bootSequence` 等旧动画状态
4. 保留：权限请求、系统 Intent 发起、Repository 初始化、ViewModel 绑定
5. 目标行数：~200 行

**验收**：编译通过，应用完全正常

---

### Phase 10 · 清理删除

按第七节"完全删除"清单逐文件移除，每次删除后编译验证。

**最终验收命令**：
```bash
./gradlew :app:assembleDebug --no-daemon
# 零编译警告（涉及 render/ 的 import 全部消失）
```

---

## 九、关键技术风险与对策

| 风险 | 说明 | 对策 |
|---|---|---|
| **字体度量差异** | 旧 `GlyphStyle.UI_SMALL_10` 的 `cellHeight`/`narrowAdvanceWidth` 与 pixelcore 字体不同 | Phase 2 时在 demo 做视觉对比；若差异明显，在 pixelcore 添加对应字号的 BitmapFont 资源 |
| **SMS 发送集成** | `TextField` + 键盘 + 短信发送是最复杂的组合 | Phase 6.4 单独排期，预留充足时间；先用旧路径保底 |
| **D-Pad 导航** | 旧的键盘事件路由全在 `MainActivity.onKeyDown`，迁移后需要在 widget 层重新实现 | Phase 8 专项处理，pixel-engine 的 `PixelSystemAction` 作为入口 |
| **充电动画帧率** | 旧渲染有 `chargeTick` 动画节拍，新渲染依赖 `postInvalidateOnAnimation` | `BatteryDivider` 用 `StatefulWidget` + `postInvalidateOnAnimation` 驱动 tick |
| **Boot 序列动画** | 旧渲染有开机扫描线动画 | 暂时直接进入 HOME，开机动画作为可选后续需求 |

---

## 十、不在本次计划范围内

- IDLE 流体物理（JBox2D）重新实现
- 开机扫描线动画
- OpenGL 渲染路径（彻底废弃）
- 充电特效（CASCADE / HORIZON / STACK 等 6 种）
- `PixelGlDisplayView` 相关的任何功能
