# pixel-engine 文档目录

这里集中放置 `pixel-engine` 的开发者文档和维护者文档。模块根目录只保留 [README](../README.md) 作为 SDK 入口说明。

## SDK 使用文档

| 文档 | 用途 |
|---|---|
| [快速入门](快速入门.md) | 从 Activity 接入 `pixel-engine`，覆盖基础布局、状态、列表和主题 |
| [组件目录](组件目录.md) | 查看公开 widget、参数签名和典型用法 |
| [主题系统](主题系统.md) | 理解 `ThemeData`、tokens、组件 style 和覆盖优先级 |
| [状态管理](状态管理.md) | 使用列表、分页、输入框 controller 和 state |
| [扩展开发](扩展开发.md) | 自定义 `RenderObject`、字体栅格器、手势策略和帧调度 |
| [架构说明](架构说明.md) | 理解 Widget / Element / RenderObject 三层模型和渲染管线 |

## 维护者文档

| 文档 | 用途 |
|---|---|
| [接手任务清单](接手任务清单.md) | 当前迭代任务、优先级、历史决策和接手建议 |

## 设计文档

| 文档 | 用途 |
|---|---|
| [变高列表虚拟化](设计/变高列表虚拟化.md) | `ListViewBuilder(estimatedItemExtent)` 的兼容 lazy 设计 |
| [文本系统 v2](设计/文本系统-v2.md) | internal paragraph model、文本测量和 ellipsis 统一设计 |
| [滚动手势 v2](设计/滚动手势-v2.md) | nested scroll session、handoff 和 scroll physics 语义设计 |
