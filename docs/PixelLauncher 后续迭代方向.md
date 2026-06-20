# PixelLauncher 后续迭代方向

日期：2026-06-20

本文记录 PixelLauncher 已确认的产品方向、边界、迭代路线和验收标准。它不是建议稿，也不是完整实现任务清单；后续功能实现应先对应到本文条目，再进入具体设计和开发。内容只基于 PixelLauncher 当前设计、现有代码能力和后续产品取舍，不再参考其他 Launcher 的功能形态。

## 1. 已确认的产品边界

当前确认的方向：

- PixelLauncher 是像素风、文本优先、低干扰的 Android 桌面启动器。
- 不做传统图标墙。
- 不做自由 widget 大屏。
- Home 只显示重要信息，优先回答“现在是否有事”。
- Drawer 只做 App 搜索，不升级为联系人、短信、计算器、网页搜索等复杂入口；增强项只做 App 别名和重命名。
- SMS 是完整短信功能，不只是 Launcher 的轻入口。
- Idle 可以继续强化，支持充电自动进入和一段时间无操作后自动进入，默认无操作 30s 自动进入，触发规则通过 Settings 配置；暂时不做防烧屏。
- Notification Listener 进入路线，用于通知摘要和状态判断。
- 数字健康只做使用时长和打开次数，不做冷却、拦截或夜间隐藏 App。
- UI 需要形成明确规范，避免固定尺寸、文字裁切、列宽压缩和 padding 不一致；规范单独维护在 `docs/PixelLauncher UI规范.md`。
- 字体大小和 fontStyle 不作为用户设置项，字体尺寸由 UI 通过 enum 选择。
- root 替代系统锁屏不在 Launcher 中实现，暂不纳入迭代路线。
- Settings 承担外观、行为、权限、数据源和高级功能的控制中心。
- 主 UI 继续由 pixel-engine 渲染，不切 Compose，不混 Android 原生控件树。

工程边界：

- 单 Activity。
- 单一 `LauncherState`。
- `LauncherViewModel` 持有唯一状态源。
- `LauncherUiState` 作为渲染投影。
- `LauncherRootHost` 统一装配 Home / Drawer / Settings / SMS / Idle / Diagnostics。
- `data` 层读取系统服务、权限、网络和持久化数据。

## 2. 锁屏与 Root 边界

结论：有 root 权限后，技术上可以做“锁屏替代”或“锁屏覆盖”，但这不属于 PixelLauncher 当前产品路线。Launcher 只保留桌面、Drawer、Idle、Settings、SMS 等既有能力；root 替代系统锁屏暂不考虑，也不要在 Launcher 主 App 中新增相关实现。

单独技术评估已归档在 `docs/Root替代系统锁屏方案.md`。该文档只作为后续独立项目或系统级实验参考，不是 PixelLauncher 的开发计划。

### 2.1 普通 App 能做什么

普通 Android App 可以：

- 设置 Activity 在锁屏上显示。
- 请求系统 Keyguard 解锁。
- 在用户解锁后恢复到自己的界面。
- 作为默认 Launcher 接管 Home。

普通 App 不能可靠地：

- 完整替代系统 Keyguard。
- 接管系统 PIN / password / pattern 验证。
- 接管生物识别、Trust Agent、加密解锁、紧急呼叫、锁屏通知权限等系统安全链路。

Android 官方 API 的方向也是“显示在锁屏上”或“请求 dismiss keyguard”，不是让三方 App 成为安全锁屏本体。

参考：

- Android `KeyguardManager`：https://developer.android.com/reference/android/app/KeyguardManager
- Android `DevicePolicyManager`：https://developer.android.com/reference/android/app/admin/DevicePolicyManager

### 2.2 Root 后能做什么

有 root 后，可以更进一步：

- 开机或亮屏时强制拉起独立锁屏 Activity。
- 隐藏系统状态栏、导航栏或替代部分 SystemUI 表现。
- 通过 root 命令、Magisk 模块或系统文件修改弱化/绕过系统 Keyguard。
- 在特定 ROM 上改 SystemUI / Keyguard 组件。

但这些能力有代价：

- 设备/ROM/Android 版本高度相关。
- OTA 后容易失效。
- 一旦禁用系统 Keyguard，设备安全性可能直接下降。
- 真正安全的锁屏替代需要接入系统 credential、biometric、emergency call、FBE 解密和 SystemUI 生命周期，工程量接近维护一个系统组件。
- Debug 成本高，失败时可能导致黑屏、无法解锁或循环启动。

### 2.3 当前决策

PixelLauncher 当前只做：

1. 保持系统 Keyguard。
2. PixelLauncher 只作为桌面、App 搜索、Idle 和轻量状态入口。
3. Idle 只按普通 Launcher 页面处理，不承担系统锁屏替代职责。
4. Settings 不提供 `ROOT REPLACE`、锁屏 hook、SystemUI 替代等入口。

暂不考虑：

- 在 Launcher 内实现 root 锁屏替代。
- 在 Launcher 内新增 `PixelLockActivity` / `PixelLockService` / `RootKeyguardController`。
- 彻底替代系统安全锁屏。
- 自己实现 PIN / password / biometric 解锁。
- 默认关闭系统 Keyguard。

仍可考虑的普通 Launcher 功能：

- 低亮度夜间 Idle。
- 充电时 Pixel Idle 常显。
- Idle 自动回到 Home。
- 系统状态栏、导航栏的普通沉浸式显示。

验收标准：

- 普通用户路径不破坏系统锁屏安全。
- Launcher 内没有 root 锁屏替代入口。
- 不为了“替代锁屏”扩大 Launcher 复杂度。

## 3. 当前能力审计

### Home

已具备：

- 日期。
- 日期下方常显天气摘要；晴、云、雾、雨、雪等状态都显示，缺少数据或定位权限时保留可操作占位。
- 下一次闹钟。
- 未接来电和未读短信数量。
- 当天屏幕使用时长和打开次数。
- 终端状态文案。
- 底部 `CONTACT` / `SMS` 文本入口。
- `CALL / SMS / ALARM / BATTERY / USE` 已收敛到最多 3 行的优先级状态模型；天气使用独立常显行，不占用这 3 行。
- Home 状态行已有点击动作：短信、通话、天气权限/刷新、闹钟、电池和使用统计都能进入对应入口。
- Home 状态行长按已有轻量短提示，例如 Usage access、Rain refresh、SMS inbox 等动作说明；提示统一占用全局状态栏文字行并自动消失。
- Home 的 `RAIN` / `USE` 点击动作已有轻量刷新反馈：刷新中、更新时间、无权限或不可用状态通过状态栏临时消息显示。

刚移除：

- `FOCUS` 上下文卡片。

主要缺口：

- 数据失败和后台刷新失败还没有明确可见状态。
- Home 还没有真正形成“打开手机后 1 秒内知道是否有事”的决策层。

### Drawer

已具备：

- 文本应用列表。
- 顶部共享搜索栏。
- 中文、拼音全拼、拼音首字母、英文标签、包名/Activity 派生字段搜索。
- 轻量排序与本地化标签。
- 左/中/右对齐设置。
- 点击应用直接启动。
- App 显示名重命名。
- 用户自定义搜索别名。
- Settings 和 Drawer 长按均可进入 App 管理。

已确认边界：

- Drawer 只做 App 搜索。
- 不搜索联系人。
- 不搜索短信线程。
- 不搜索设置项。
- 不做计算器、命令行、网页搜索。
- 不做复杂分类页。

主要缺口：

- App 管理仍是编辑页形态，还没有 Playdate 式轻量浮层菜单。
- 还缺围绕“快速定位并打开 App”的长按菜单交互规范。

### Settings

已具备：

- 像素尺寸使用 pixel-engine `SegmentedControl`，保留原有可选分辨率集合并按窄屏分行。
- 像素间隙只保留 `ON / OFF` Switch，不再提供比例 Slider。
- 像素形状。
- 主题。
- 应用列表对齐。
- Drawer 默认搜索。
- Home 状态入口。
- Idle 开关和 Idle effect。
- Display / Home / Drawer / Idle / Data / Advanced 分组。
- Data Health 入口，展示最近刷新时间、Usage、Location、Call Log、SMS、Notification POST、Notification Listener 等状态，异常行显示 Android 侧短原因，并提供基础权限/角色修复入口。
- App 管理入口：别名、重命名和缓存刷新。
- Idle 自动进入配置：充电自动进入、无操作自动进入和超时时长。
- Advanced 入口。
- Advanced / Diagnostics 展示 Home 摘要、Data Health 摘要、启动统计、受控字体 metrics、关键文案宽度采样、display、status bar inset 和电量状态。

已调整：

- 字体风格切换已移除。
- 字体大小和 fontStyle 不作为用户设置项。
- 字体尺寸由 UI 通过受控 enum 选择。

主要缺口：

- Data Health 已有基础错误状态、最近刷新时间和 Android 侧短原因；还缺后续 Notification Listener 摘要规则。
- UI / 字体诊断已有基础 metrics 和关键文案宽度采样；还缺真实渲染 bounds 检查。
- Advanced 已有基础状态总览和文本采样；还缺可操作的调试入口和更细的 bounds 验证。

### Idle

已具备：

- 时间、日期、电量、使用时长。
- 状态优先级：通知 > 充电 > 需关注天气 > 默认。
- 充电状态显示电量和当前 Idle effect 名称。
- 支持充电自动进入 Idle。
- 支持无操作自动进入 Idle，默认 30s，Settings 可配置。

主要缺口：

- Idle effect 目前更多是选项名称，不是完整视觉状态系统。
- 充电、通知、需关注天气还没有精细状态，例如充电预计完成、暴雨提醒。
- 没有低亮度/夜间简化模式。
- Idle 还没有和 Home / Settings 形成完整的待机体验闭环。

### SMS

已具备：

- 默认短信角色申请。
- 线程列表。
- 会话详情。
- 未读收件箱。
- 草稿输入与发送。
- 首次进入 loading 状态。
- 长短信正文换行已从 pixel-engine 根因修复。

主要缺口：

- 未读短信摘要与 Home 的关系还可以更紧密。
- 联系人名称映射、验证码识别/快速复制、线程搜索、发送失败状态、附件/长短信边界处理还没有。
- SMS 子流程是独立页面，不进入 Drawer 搜索。

## 4. 产品原则

### 原则 1：Home 只回答“现在是否有事”

Home 不应该成为 Dashboard。它应该用极少信息回答：

- 现在几点。
- 今天是否有必须处理的事。
- 手机是否正在诱导继续使用。
- 是否有重要通信、天气风险或低电量。

Home 的状态优先级固定为：

1. 紧急通信：未接电话、重要短信。
2. 时间约束：下一次闹钟、日程、倒计时。
3. 环境风险：降雨、极端天气、低电量。
4. 自我约束：今日使用时间、打开次数、使用提醒。
5. 默认安静状态。

### 原则 2：Drawer 只优化 App 搜索

Drawer 的目标是最快启动 App，而不是成为全局搜索入口。

确定加强：

- App 搜索质量。
- App 别名。
- App 显示名重命名。
- App 缓存刷新。

不做：

- 联系人搜索。
- SMS 搜索。
- 设置搜索。
- 计算器。
- 命令行。
- 网页搜索。
- App 分类管理。
- App 隐藏策略。
- 工作资料复杂管理。

### 原则 3：通知只做摘要和降噪

不做完整通知中心。Notification Listener 要进入路线，但只用于摘要和降噪：

- 未读计数。
- 高优先级摘要。
- 通知来源过滤。
- Home 上一行摘要。
- 点击进入系统通知面板或对应 App。

初版摘要规则已确定：

- 静音来源不进入摘要。
- ongoing 常驻通知不进入摘要。
- 只保留系统高优先级通知，或显式配置为高优先级的来源。
- 摘要最多展示 2 个来源，剩余用 `+N` 表示。
- Home / Idle 只显示一行 `NOTIFY ...`，不展示完整通知流。
- `NotificationListenerService` 已通过进程内摘要桥接写入 Home / Idle 状态字段。

### 原则 4：Settings 同时补数据健康和 App 管理

当前外观设置已经够多。下一阶段 Settings 应该同时补齐：

- 权限状态。
- 数据源状态。
- 默认短信角色状态。
- Usage Access 状态。
- Notification Listener 状态。
- App 别名。
- App 重命名。
- Idle 自动进入配置。
- UI / 字体诊断入口。

不再进入 Settings：

- 字体大小选择。
- `MONO` / `PROP` 字体风格选择。

### 原则 5：系统级实验不进入 Launcher 主路径

root、SystemUI hook、锁屏替代这类系统级实验不要混入 Launcher 主路径：

- 不在 Settings 中暴露 root 锁屏替代入口。
- 不在 Launcher 进程中承载锁屏 hook 生命周期。
- 不把 Idle 扩展成系统锁屏。
- 后续如要研究，作为独立模块或独立项目评估。

### 原则 6：UI 先有规范再扩页面

后续 UI 必须避免重复出现文字裁切、固定尺寸、列宽压缩和 padding 不一致的问题：

- 文本容器不使用拍脑袋固定宽高。
- 按钮、列表行、分栏列宽必须优先使用组件自身测量、约束和 `Expanded`。
- 文字必须垂直居中，边框内至少保留稳定 padding。
- 两栏布局中左栏按内容占用，右栏使用剩余空间。
- 全局状态栏 padding 只在宿主顶部处理，不给整个 UI 递归加 padding。
- 每个新增页面都要在真机截图自查文字是否被裁切。

## 5. 已确认迭代路线

### 阶段 A：补齐当前体验闭环

目标：不扩大产品野心，先把现有 Home / Drawer / Settings / Idle / SMS 的基础闭环做完整。

确定功能：

1. Home 优先级状态行
   - 把 `CALL / SMS / RAIN / ALARM / USE / BATTERY` 收敛成明确排序的状态模型。
   - 默认只显示重要信息，最多显示 3 行。
   - 每行可以有点击行为。

2. Home 行点击动作
   - `SMS n` 打开未读短信或短信列表。
   - `CALL n` 打开通话记录或联系人。
   - `RAIN ...` 在状态行显示天气摘要和最近刷新时间；缺定位权限时显示 `RAIN LOC`，点击请求权限或刷新天气。
   - `USE ...` 打开使用统计说明或系统 Usage Access 设置。
   - `RAIN` / `USE` 点击后通过状态栏临时消息反馈刷新中、刷新结果或缺权限状态。
   - 长按状态行通过状态栏显示临时消息，不改变点击动作。

3. Settings 数据健康分组
   - Usage Access：`READY` / `NO ACCESS`，点击打开系统设置。
   - Location：`READY` / `NO PERM`，点击请求或打开系统设置。
   - Call Log：`READY` / `NO PERM`。
   - SMS：权限状态 + 默认短信角色。
   - Notification：通知监听状态。
   - 异常项显示短原因，例如 `RUNTIME PERM`、`DEFAULT SMS ROLE`、`LISTENER ACCESS`。

4. App 管理基础能力
   - 重命名显示标签。
   - 设置搜索别名。
   - Settings 和 Drawer 长按菜单都提供入口。
   - 重置 App 缓存。

5. Idle 自动进入配置
   - 充电时自动进入 Idle。
   - 无操作 30s 后自动进入 Idle。
   - 自动进入时长可以在 Settings 配置。
   - 暂时不做防烧屏。

6. Idle 完整状态细化
   - 低电量态。
   - 充电态显示电量变化，而不是只显示 effect 名称。
   - 通知态只显示数量和来源，不展开详情。
   - 夜间更低亮度或更稀疏布局。

7. UI 规范落地
   - `docs/PixelLauncher UI规范.md` 已作为 UI 验收基线。
   - 统一按钮、列表行、分栏、状态栏 padding、文本裁切规则。
   - Settings 不暴露字体大小 / fontStyle。
   - 字体尺寸由 UI 通过 enum 选择。
   - 明确禁止文本按钮使用不可靠固定高度。
   - 每个新增页面做真机截图检查。

验收标准：

- 首页信息更少但更明确。
- 用户不用进入设置就能理解哪些数据缺权限。
- Drawer 仍然只负责 App，但用户能用别名和重命名快速定位 App。
- 充电和无操作 30s 能按设置进入 Idle。
- 新增 UI 不出现文字裁切、边框贴字或左右栏压缩。

### 阶段 B：Drawer App 搜索增强

目标：不复杂化 Drawer，只把 App 搜索做到高质量。

确定功能：

1. App 别名
   - 用户可为 App 添加多个搜索别名。
   - 别名只影响搜索，不一定改变显示名。
   - Settings 和 Drawer 长按菜单都能编辑。

2. App 重命名
   - 用户可覆盖显示标签。
   - 用于处理系统标签过长、不准确或中英文不一致。
   - Settings 和 Drawer 长按菜单都能编辑。

3. 搜索命中解释
   - 保留在搜索模型、排序调试和测试中，用于确认命中来源。
   - 不在 Drawer 列表行中显示 `PINYIN`、`ALIAS`、`PACKAGE` 等标签。
   - Drawer 列表只显示 App 标题，避免别名和命中原因挤占主标题空间。

验收标准：

- Drawer 没有额外分类层级。
- 搜索仍然只返回 App。
- 搜索结果更符合用户自己的命名习惯。
- 搜索结果列表只显示 App 标题，重命名后的标题优先。

### 阶段 C：Idle 体验完善

目标：让 Idle 成为 PixelLauncher 的视觉重点，但不承担系统锁屏替代职责。

确定功能：

1. Pixel Idle 状态系统
   - 默认态。
   - 充电态。
   - 低电量态。
   - 天气风险态。
   - 通知摘要态。

2. 自动进入规则
   - 充电自动进入。
   - 默认无操作 30s 自动进入。
   - Settings 中配置开关和超时时长。

3. 夜间模式
   - 夜间降低亮度。
   - 充电常显时降低信息密度。

验收标准：

- Idle 作为 Launcher 内部页面稳定可用。
- 系统锁屏仍完全交给 Android。
- 不自己实现安全凭据校验或锁屏替代。

### 阶段 D：通知摘要与数字健康

目标：把通知、使用时长和打开次数变成 Home / Idle 可用的状态来源。

确定功能：

1. Notification Listener 可选接入
   - 只统计高优先级来源。
   - 支持 App 级静音和摘要。
   - Home 只显示摘要，不显示完整通知列表。
   - 初版摘要规则已落地为高优先级 / 显式优先来源 / 静音过滤 / ongoing 过滤 / 最多两项。
   - Listener service 已接入摘要桥接，前台 Activity 会订阅并刷新 Home / Idle 摘要。

2. 使用时长
   - 统计今日使用时长。
   - Home 可展示简短摘要。
   - Settings / Diagnostics 可展示来源和权限状态。

3. 打开次数
   - 统计今日打开次数。
   - Home 可展示简短摘要。
   - 不做冷却、拦截或夜间隐藏 App。

验收标准：

- 通知摘要不变成完整通知中心。
- 使用时长和打开次数只做展示，不阻止用户完成任务。
- 所有数据来源都可解释、可关闭。

### 阶段 E：完整 SMS 能力

目标：SMS 作为 Launcher 内置完整短信功能，而不是只显示列表和详情。

确定功能：

1. 线程与会话体验
   - 联系人名称映射。
   - 未读优先和 Home 直达。

2. 消息操作
   - 验证码识别和快速复制。
   - 正文选择、复制、重发。
   - 发送中、发送失败、已发送状态。

3. 线程搜索
   - 按联系人名、号码、正文搜索。
   - 只在 SMS 内部搜索，不进入 Drawer。

4. 边界处理
   - 长短信分页或稳定换行。
   - 空状态、loading、错误状态一致。
   - 权限缺失和默认短信角色缺失可见。

验收标准：

- 首次进入、空线程、长文本、发送失败都不是空白或裁切状态。
- Home 的 SMS 摘要能直达对应短信视图。
- SMS 不进入 Drawer 搜索，Drawer 仍然只搜索 App。

## 6. 明确不做的功能

### 不做传统图标桌面

原因：

- 会削弱像素文本体验。
- 会把维护重点从状态与搜索转移到图标、网格、文件夹和动画细节。

### 不做自由 widget 大屏

原因：

- 自由 widget 会带来复杂布局、生命周期、权限和性能问题。
- PixelLauncher 更适合少量官方状态块。

### 不做完整通知中心

原因：

- Android 系统通知面板已经承担这个职责。
- Launcher 侧做完整通知会带来隐私和权限复杂度。
- PixelLauncher 更适合做过滤、摘要和直达。

### 不做默认联网搜索

原因：

- Drawer 已确认只做 App 搜索。
- 联网搜索会让 Drawer 变慢且不可预测。
- 也会引入隐私和网络失败问题。

### 不在 Launcher 中处理系统锁屏替代

原因：

- 真正安全锁屏属于系统安全链路。
- root 替代方案设备差异大。
- 失败风险高，可能影响解锁。
- PixelLauncher 更适合做桌面、App 搜索和 Idle，而不是安全认证系统或锁屏替代系统。

### 不引入复杂主题市场

原因：

- PixelLauncher 的视觉识别依赖统一像素风。
- 当前更需要稳定字体、尺寸、可读性和主题 token，而不是无限外观参数。

### 不做强干预数字健康

原因：

- 当前只确认做使用时长和打开次数。
- 冷却、拦截、夜间隐藏 App 会改变 Launcher 的行为预期。
- Drawer 的目标是快速打开 App，不应该在启动路径上增加阻断。

## 7. 实施优先级

### P0：近期必须推进

1. Settings 数据健康页
   - 把 Usage Access、Location、Call Log、SMS、Notification Listener 状态统一展示。
   - 缺失项显示 Android 侧短原因。
   - 理由：当前功能依赖权限和系统能力，用户需要知道为什么信息为空。

2. Home 状态行点击动作
   - `SMS`、`CALL`、`RAIN`、`USE` 可进入对应动作或说明。
   - 理由：现有 Home 已有信息，但缺行动闭环。

3. App 别名与重命名
   - Drawer 支持别名搜索、重命名。
   - Settings 和 Drawer 长按菜单都提供编辑入口。
   - 理由：Drawer 只做 App 搜索，更需要把 App 管理做好。

4. Idle 自动进入配置
   - 充电自动进入、无操作 30s 自动进入。
   - 开关和超时时长放在 Settings。

5. UI 规范落地
   - 按 `docs/PixelLauncher UI规范.md` 统一按钮、列表、分栏、文本裁切、状态栏 padding 规则。
   - Settings 不提供字体大小和 fontStyle 用户选项。
   - 字体尺寸使用受控 enum。

### P1：差异化能力

1. 通知摘要
   - 可选 Notification Listener。
   - 只做摘要和过滤，不做完整通知流。
   - 初版过滤规则和 listener bridge 已落地；后续补 Settings 中的 App 级静音和优先来源配置。

2. 使用时长与打开次数
   - 只做展示和 Home 摘要。
   - 不做冷却、拦截或夜间隐藏 App。

3. 完整 SMS
   - 优先级：联系人映射 -> 验证码复制 -> 线程搜索。
   - 同步补发送失败状态、Home 直达。

### P2：长期探索方向

1. 官方状态块
   - 日程、媒体、Todo、倒计时。
   - 保持同屏最多一个主状态块。

## 8. 待明确问题

以下问题尚未定细节，进入对应功能实现前需要确认：

1. Home 重要信息的优先级是否按通信、时间、环境、使用统计排序？
2. Settings 中 Notification Listener 的 App 级静音和优先来源配置如何组织？
3. SMS 联系人映射的数据来源优先使用系统联系人，还是先只做号码归一化？
4. UI 规范落地后，先修 Launcher 主 App，还是同时约束 pixel-demo？

## 9. 决策结论

PixelLauncher 下一阶段不扩成“万能桌面”。已确认的方向是：

- Home：从信息行升级为优先级状态页。
- Drawer：保持 App 搜索，不做全局命令入口。
- Settings：从外观设置扩展为数据健康、App 别名/重命名和 Idle 自动进入配置中心。
- Drawer：App 别名和重命名同时支持 Settings 与 Drawer 长按入口。
- Idle：支持充电自动进入和默认无操作 30s 自动进入，但不作为系统锁屏替代基础。
- SMS：作为完整短信功能继续推进，优先做联系人映射，其次验证码复制，再做线程搜索。
- Root：不进入 Launcher 当前实现路线；root 锁屏替代只保留在独立技术评估文档中。
- Notifications：只做摘要、过滤和直达，不做完整中心。
- Usage：只做使用时长和打开次数，不做强干预。
- Widgets：只做官方状态块，不开放自由 widget。

这个路线能保持 PixelLauncher 的独特性：像素文本、低干扰、快 App 搜索、强状态感，同时避免陷入传统 Launcher 的图标、文件夹、主题、widget 和全局搜索维护负担。
