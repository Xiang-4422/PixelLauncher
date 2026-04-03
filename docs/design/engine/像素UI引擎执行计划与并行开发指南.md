# 像素 UI 引擎执行计划与并行开发指南

这份文档服务于两个目标：

- 把当前阶段的后续工作拆成可以直接执行的工作包
- 给多人并行开发提供明确的分支边界、写入范围和合入顺序

如果只看一句话，当前阶段的唯一主线是：

> 先把 `pixel-ui` 做成稳定的“retained framework + 最薄 bridge + 最薄 legacy renderer”中间态，再启动真正的新渲染管线建设。

---

## 1. 当前执行原则

当前阶段默认遵守以下原则：

- 不启动 `:app` 迁移
- 不让 `:app` 重新依赖 `:pixel-core` 或 `:pixel-ui`
- 不回头扩张旧 `Pixel*` 公开 API
- 不急着删除 `legacy`
- 不在同一轮里同时重写 retained framework 和新 renderer

当前的代码真实状态是：

- `retained` 已成立
- `bridge` 已经成为明确过渡层
- `legacy renderer` 仍然是当前真实出图后端
- `pixel-demo` 仍然是唯一正确验收面

---

## 2. 当前阶段工作拆分

### 工作包 A：retained framework 收口

目标：

- 继续把 retained 主链收成真正清晰的 framework 层
- 为后续 `RenderObject / PipelineOwner` 铺路

范围：

- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained`
- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime`
- retained 相关测试

允许修改：

- `BuildOwner`
- `Element`
- `Stateful / Inherited / InheritedNotifier`
- dirty build、依赖跟踪、binding、slot、registry
- retained runtime 的 request / assembly / support 协议

不处理：

- legacy renderer 细节
- legacy widget 兼容层

完成定义：

- retained/runtime 主链职责继续变薄
- 目录和文档保持同步
- retained 主链类和方法注释持续补齐

建议分支名：

- `codex/engine-retained-framework`

### 工作包 B：bridge 继续压薄

目标：

- 继续把 bridge 收成最薄兼容层
- 避免 bridge 演变成第二套 runtime

范围：

- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge`
- bridge 相关测试与文档引用

允许修改：

- resolve
- bridge element
- bridge widget adapter
- bridge runtime / assembly / request
- modifier merge/apply

不处理：

- retained framework 内部状态机制
- legacy renderer 具体绘制逻辑

完成定义：

- bridge 更像纯桥接执行层
- 解析协议、运行协议、widget 兼容壳边界更清楚

建议分支名：

- `codex/engine-bridge-thin`

### 工作包 C：legacy renderer façade 继续收口

目标：

- 继续压薄当前 legacy renderer
- 把大类继续拆成调度 + helper

范围：

- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy`
- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy`

允许修改：

- runtime / layout / viewport / measure / node / text
- legacy widgets / model
- support assembly / callbacks / session / dispatcher

不处理：

- retained framework 的状态与环境协议
- bridge 与 retained 之间的顶层装配

完成定义：

- `PixelRenderRuntime` 继续维持 façade 角色
- support/assembly/helper 结构更稳定
- legacy 目录继续只作为内部兼容层存在

建议分支名：

- `codex/engine-legacy-render`

### 工作包 D：测试、文档、注释与验收

目标：

- 给前面三条主线持续兜底
- 防止结构变化快于文档和测试

范围：

- `docs/design/engine`
- `pixel-ui/src/test`
- `pixel-demo`

允许修改：

- 进度文档
- 实施计划文档
- 执行计划文档
- demo 真机验证说明
- retained / bridge / legacy 相关单测
- 主链类和方法注释

不处理：

- 不做结构性重构
- 不修改主链实现语义，除非是为测试修正明显错误

完成定义：

- 文档与目录结构同步
- 关键场景回归持续可见
- 注释覆盖不再只靠“碰到再补”

建议分支名：

- `codex/engine-docs-tests`

---

## 3. 多人并行开发规则

可以拆分并行做，但必须按写入边界来。

推荐的并行组合：

1. 一人负责工作包 A
2. 一人负责工作包 B
3. 一人负责工作包 C
4. 一人负责工作包 D

这样拆的原因是：

- `A` 主要写 `retained/runtime`
- `B` 主要写 `bridge`
- `C` 主要写 `legacy renderer + legacy compat`
- `D` 主要写文档、测试、demo

冲突风险最低。

不建议的拆法：

- 两个人同时改 `retained/runtime`
- 两个人同时改 `legacy renderer/runtime`
- 一边改主链结构，一边在同文件里集中补注释

这类拆法会让冲突成本高于并行收益。

---

## 4. 合入顺序与门槛

推荐合入顺序：

1. `engine-retained-framework`
2. `engine-bridge-thin`
3. `engine-legacy-render`
4. `engine-docs-tests`

原因：

- retained 和 bridge 是上游
- legacy renderer 是当前后端实现
- 文档/测试最后补一轮最稳

每个分支合入前必须通过：

```bash
./gradlew :pixel-core:testDebugUnitTest :pixel-ui:testDebugUnitTest :pixel-engine:assembleDebug :pixel-demo:assembleDebug --no-daemon
```

真机门槛：

```bash
adb -s <device> install -r /Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/build/outputs/apk/debug/pixel-demo-debug.apk
adb -s <device> shell am start -n com.purride.pixeldemo/.app.DemoMenuActivity
```

最低验收要求：

- demo 菜单可启动
- 文本页可打开
- 输入页可打开
- 列表/分页页可打开

---

## 5. 下一阶段目标

当前这些工作包做完后，下一阶段才进入：

- `RenderObject`
- `PipelineOwner`
- layout / paint / hitTest 新协议
- 新 renderer 承接最小组件集

在这之前，不做：

- `:app` 页面迁移
- 删除整套 `legacy`
- 一次性重写 `PageView / ListView / TextField`

一句话说，当前阶段的目标不是“把新 renderer 一口气写完”，而是：

> 把现在的中间态整理成可长期迭代、可多人协作、可安全替换后端的稳定架构。
