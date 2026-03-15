# PixelLauncher 设计文档

这组文档用于定义 PixelLauncher 的产品层设计基线。

当前项目的底层渲染、状态机、字体和基础动画已经具备可复用性，后续设计默认沿用现有技术底盘，重点重做以下产品模块：

- Home
- Drawer
- Idle
- Settings
- MVP 任务拆分

## 产品目标

- 减少手机使用时间
- 建立统一、优雅、精致的像素体验
- 用像素风格去除现代 UI 的繁杂
- 通过 Home 和 Idle 减少为了确认状态而打开 App 的次数

## 目标设备

- 手机
- 优先适配 1:1 比例安卓设备
- 当前只考虑竖屏
- 需要同时考虑中文和英文

## 设计原则

- 像素 UI 的完成度和精致度优先
- 界面应偏情绪化设备体验，而不是传统效率桌面
- 信息应尽量符号化，减少噪音
- 功能上优先“少但稳定”
- 不将 Home 或 Idle 做成复杂仪表盘

## 当前范围

- 保留现有底层渲染与状态管理能力
- 去掉 Boot 自检的主路径角色
- Diagnostics 降级到 Advanced
- 暂不讨论 root 下替换系统锁屏
- 暂不纳入备份恢复、商业化、复杂 TODO 系统

## 主入口

- [产品总规约 v1](./product-spec-v1.md)

## 文档列表

- [工程分工与工作包 v1](./engineering-work-allocation-v1.md)
- [Home PRD v1](./home-prd-v1.md)
- [Drawer PRD v1](./drawer-prd-v1.md)
- [Idle PRD v1](./idle-prd-v1.md)
- [Settings IA v1](./settings-ia-v1.md)
- [MVP 任务拆分](./mvp-task-breakdown.md)

## 当前代码对应关系

- 主状态入口：`app/src/main/java/com/purride/pixellauncherv2/app/MainActivity.kt`
- 启动器状态：`app/src/main/java/com/purride/pixellauncherv2/launcher/LauncherState.kt`
- 状态切换：`app/src/main/java/com/purride/pixellauncherv2/launcher/LauncherStateTransitions.kt`
- 渲染主入口：`app/src/main/java/com/purride/pixellauncherv2/render/PixelRenderer.kt`
- Home 现状：`app/src/main/java/com/purride/pixellauncherv2/launcher/HomeLayout.kt`
- Drawer 现状：`app/src/main/java/com/purride/pixellauncherv2/launcher/AppListLayout.kt`
- Settings 现状：`app/src/main/java/com/purride/pixellauncherv2/launcher/SettingsMenuLayout.kt`
- Idle 动画现状：`app/src/main/java/com/purride/pixellauncherv2/render/IdleFluidEngine.kt`

## 使用建议

- 工程启动前优先阅读“产品总规约 v1”
- 排任务和领任务时优先阅读“工程分工与工作包 v1”
- 模块开发时再进入对应的 Home、Drawer、Idle、Settings 文档
- 排期和拆分以“MVP 任务拆分”为准
