# ADR-0002：进程死亡易失草稿的持久化白名单

- 状态：提议（产品语义待决，未实施任何生产代码）
- 起草日期：2026-07-31
- 范围：`:app` 中跨进程死亡会丢失、且用户可感知损失的输入草稿与操作暂存
- 非目标：不引入全量状态序列化；不改变 ADR-0001 的切片迁移顺序；不处理
  provider/repository 可重新装载的数据（它们不是"损失"）

## 1. 背景

ADR-0001 §4.5 已确认：`LauncherViewModel` 无 `SavedStateHandle`，`MainActivity` 无
`onSaveInstanceState` 状态恢复，所有 `VM` 寿命字段只能跨配置变更、不能跨进程死亡。
对 Launcher（HOME 应用）整体而言这是合理取舍——桌面总是可以冷启动重建；但其中
少数字段承载用户逐字输入的内容，进程死亡即真实数据损失。本 ADR 盘点这些字段，
为"是否恢复、恢复到哪、何时清除、能否落盘"建立白名单决策框架。

## 2. 易失草稿完整盘点

| # | 草稿 | 当前载体 | 寿命 | 损失影响 | 敏感性 |
|---|---|---|---|---|---|
| 1 | 短信草稿（按会话） | `SmsDraftStore`（内存 Map，SmsController.kt:91）+ 当前会话的 `state.smsDraftText` | 进程 | 高：逐字输入的通信内容 | **高**：通信正文 + 会话身份 |
| 2 | 联系人编辑草稿 | `state.contactEditorNameDraft` / `contactEditorNumberDraft` | 进程（VM 跨配置） | 中：姓名/号码输入 | **高**：PII（姓名、电话号码） |
| 3 | 应用编辑草稿 | `state.appEditorNameDraft` / `appEditorAliasDraft` | 进程（VM 跨配置） | 低：通常很短，保存后即入 `AppCustomizationRepository` | 低 |
| 4 | 外观预览 rollback baseline | `MainActivity.pendingPixelAppearanceBaseline`（Activity 字段，MainActivity.kt:160） | **Activity**（配置重建即丢） | 中：预览态与 baseline 寿命不一致——appearance 本体在 VM 跨配置保留，baseline 不保留，重建后确认/回滚倒计时行为存疑（ADR-0001 §4.5 已登记） | 低 |
| 5 | 抽屉搜索词 `drawerQuery` | `state`（VM） | 进程 | 极低：重输成本秒级 | 低 |
| 6 | 拨号输入 `dialInput` | `state`（VM） | 进程 | 低：号码可重输，但中断体验差 | 中：拨号号码 |
| 7 | 短信搜索词 `smsThreadSearchQuery` | `state`（VM） | 进程 | 极低 | 中：搜索词可能含通信内容 |
| 8 | 贪吃蛇最高分 | `SnakeController`（进程级，注释"持久化留待后续"） | 进程 | 低：娱乐数据 | 无 |

## 3. 决策框架（提议）

按"损失影响 × 敏感性"分三档处理：

### 3.1 建议进入持久化白名单（需产品确认）

- **#1 短信草稿**：唯一高影响项。提议按会话键落盘到专门仓库
  （如 `SmsDraftRepository` + 独立 SharedPreferences 文件），语义对齐现有
  `SmsDraftStore`：进入会话恢复、发送成功清除、用户清空输入即删除键。
  **敏感性约束**：与 `sms_mute_settings.xml` 同等对待——加入云备份双轨排除
  （`backup_rules` / `data_extraction_rules`），接受换机丢失换取通信内容不出本机
  （先例见 SECURITY.md）。
- **#2 联系人编辑草稿**（弱建议）：编辑会话通常短暂，`SavedStateHandle`
  跨配置已由 VM 覆盖；仅在产品确认"编辑中被杀恢复"有真实价值时落盘，同样需要
  备份排除。默认可不做。

### 3.2 建议只修寿命不一致，不落盘

- **#4 外观预览 baseline**：问题不是进程死亡，而是 baseline 在 Activity 字段、
  预览值在 VM，配置重建后两者不同步。修复方向是把 baseline 与 deadline 一并挪进
  ViewModel（跨配置一致），进程死亡时整组丢弃（预览本来就该回滚）。这属于
  ADR-0001 阶段 5 Appearance coordinator 的验收用例，不需要持久化。

### 3.3 建议明确不恢复（记录即决策）

- **#3 应用编辑草稿、#5 抽屉搜索词、#7 短信搜索词**：重输成本低于恢复机制的
  复杂度与测试面，明确不持久化、不进 SavedState。
- **#6 拨号输入**：号码含隐私且拨号会话短暂，明确不落盘；是否进 SavedState
  留产品决定，默认不做。
- **#8 贪吃蛇最高分**：可作为独立小任务落盘（单键 SharedPreferences，无敏感性），
  不属于本白名单的"草稿"范畴，仅在此登记避免遗漏。

## 4. 载体选型（提议）

| 载体 | 适用 | 不适用 |
|---|---|---|
| 专门仓库 + 独立 SharedPreferences 文件 | #1（需要备份排除粒度、按会话键、与现有四类仓库模式一致） | 高频写入场景 |
| `SavedStateHandle` | 仅覆盖系统回收重建，不覆盖用户主动杀进程；适合 #2 这类"会话中"草稿 | 需要跨启动恢复的内容 |
| `onSaveInstanceState` | 不引入：ADR-0001 已确认无实例状态恢复路径，新增会扩大测试面 | — |
| 全量状态序列化 | **禁止**（ADR-0001 §4.5：`lastInteractionUptimeMs` 等单调时钟字段不可持久化；provider 快照应重新装载） | — |

## 5. 待决产品问题（阻塞实施）

1. 短信草稿是否允许落盘（通信正文写本机存储）？若否，#1 降级为"仅内存，
   接受进程死亡丢失"并在此记录为最终决策。
2. 草稿恢复的可见性：进入会话静默回填，还是给出"已恢复草稿"提示？
3. 用户主动退出编辑（back/关闭模块）是否等同"放弃草稿"？当前 `hideSmsThreads`
   会清 `smsDraftText` 但 `SmsDraftStore` 保留副本——两者语义需统一。
4. 联系人编辑草稿是否值得任何形式的恢复（#2 默认不做）？

## 6. 实施顺序约束

- 本 ADR 的任何实施都在 ADR-0001 阶段 3 的 `SmsState` 切片迁移**之后**进行，
  避免同时移动字段所有权与新增持久化两个变量。
- 每个白名单项独立提交：仓库 + 备份排除 + 行为测试（恢复/清除/发送后清空）
  一体验收；不夹带其他草稿项。
- 落盘项必须新增 `SensitiveDataContractTest` 类别的契约覆盖（备份排除双轨与
  文件名登记），先例见 `sms_mute_settings.xml`。
