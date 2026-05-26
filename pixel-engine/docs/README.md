# pixel-engine 文档目录

这里集中放置 `pixel-engine` 的所有文档，按读者角色分三类。模块根目录只保留 [README](../README.md) 作为 SDK 入口说明。

## 使用说明（对外 SDK 使用者）

写给使用 `pixel-engine` 构建像素化应用的开发者。

| 文档 | 用途 |
|---|---|
| [快速入门](使用说明/快速入门.md) | 从 Activity 接入 `pixel-engine`，覆盖基础布局、状态、列表和颜色配置 |
| [组件目录](使用说明/组件目录.md) | 查看公开 widget、参数签名和典型用法 |
| [主题系统](使用说明/主题系统.md) | 理解当前直接 `PixelColor` 样式、宿主背景、格栅色和文本栅格器配置 |
| [状态管理](使用说明/状态管理.md) | 使用列表、分页、输入框 controller 和 state |
| [扩展开发](使用说明/扩展开发.md) | 自定义 `RenderObject`、字体栅格器、手势策略和帧调度 |

## 实现说明（SDK 维护者了解现状）

写给维护 `pixel-engine` 源码的开发者，描述当前已落地的内部架构与关键设计。

| 文档 | 用途 |
|---|---|
| [架构说明](实现说明/架构说明.md) | Widget / Element / RenderObject 三层模型和渲染管线 |
| [变高列表虚拟化](实现说明/变高列表虚拟化.md) | `ListViewBuilder(estimatedItemExtent)` 的兼容 lazy 设计 |
| [文本系统 v2](实现说明/文本系统-v2.md) | internal paragraph model、文本测量和 ellipsis 统一设计 |
| [滚动手势 v2](实现说明/滚动手势-v2.md) | nested scroll session、handoff 和 scroll physics 语义设计 |
| [WidgetTester DSL](实现说明/widget-tester-dsl.md) | test-only 离屏 widget 测试 DSL 当前能力与边界 |

测试代码可使用 `com.purride.pixelui.testing.PixelTester` 在 JVM 单测中离屏渲染
widget、触发 `tap` / `drag`、推进帧并等待动画 settle。该 DSL 只在
`src/test` 下提供，不进入 release AAR。

## 演进规划（SDK 维护者了解路线与历史）

写给规划 `pixel-engine` 下一阶段的开发者。部分文档是已完成能力的历史实施记录，保留用于理解关键取舍。

| 文档 | 用途 |
|---|---|
| [演进路线](演进规划/演进路线.md) | SDK 演进方向、当前迭代任务、优先级、历史决策和接手建议 |
| [动画方向](演进规划/动画方向.md) | 动画系统的设计取向与后续 Layer 4 方向 |
| [动画实现计划](演进规划/动画实现计划.md) | 动画系统 Layer 1-3 的历史实施记录 |
| [色彩演进](演进规划/色彩演进.md) | 从三色调色板到当前 ARGB 直出模型的历史决策 |
| [彩色实现计划](演进规划/彩色实现计划.md) | 彩色系统实施记录；当前代码已收敛为单一 ARGB 路径 |
