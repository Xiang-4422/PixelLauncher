# PixelLauncher 设计文档索引

这份文档是 `docs/design` 的唯一索引页。  
当前文档体系只保留三类内容：

- `architecture/`：仓库当前真实技术实现
- `engine/`：像素 UI 引擎的架构、进度和接入方式
- `product/`：产品目标和模块设计

如果你第一次进入项目，先读根目录 [README.md](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/README.md)，再回到这里按路径进入。

## 1. 目录分类

### architecture

- [技术实现总览](./architecture/技术实现总览.md)
- [渲染实现原理](./architecture/渲染实现原理.md)

用途：

- 解释当前仓库真实代码如何组织
- 解释当前 `app` 主链路、渲染链路和系统边界
- 需要确认“代码现在到底怎么跑”时，优先读这一层

### engine

- [像素 UI 引擎架构与实施计划](./engine/像素UI引擎架构与实施计划.md)
- [像素 UI 引擎当前进度](./engine/像素UI引擎当前进度.md)
- [像素 UI 引擎组件接入指南](./engine/像素UI引擎组件接入指南.md)

用途：

- 解释 `pixel-core / pixel-ui / pixel-demo / app` 的边界
- 解释 retained runtime、legacy render bridge 和当前重构阶段
- 解释新模块如何把 `pixel-ui` 当库接入

### product

- [产品总规约](./product/产品总规约.md)
- [主页设计](./product/主页设计.md)
- [应用抽屉设计](./product/应用抽屉设计.md)
- [待机页设计](./product/待机页设计.md)
- [设置信息架构](./product/设置信息架构.md)

用途：

- 解释产品目标、页面职责和模块设计
- 需要确认“最终想做成什么”时，优先读这一层

## 2. 已整合与删除

这轮文档整理已经做了这些收口：

- 原来的“应用抽屉完成度与待办清单”已经并入 [应用抽屉设计](./product/应用抽屉设计.md)
- 原来的“工程分工与工作包”和“最小可用版本任务拆分”已经删除

删除原因：

- 这两份任务文档属于早期路线图，和当前 retained runtime 重构阶段已经不一致
- 当前真正的执行口径已经收敛到引擎文档和当前进度文档，不再维护多套临时并行任务表

## 3. 推荐阅读路径

### 新接手工程师

1. 读 [README.md](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/README.md)
2. 读 [技术实现总览](./architecture/技术实现总览.md)
3. 读 [像素 UI 引擎架构与实施计划](./engine/像素UI引擎架构与实施计划.md)
4. 读 [像素 UI 引擎当前进度](./engine/像素UI引擎当前进度.md)
5. 如果要直接基于引擎开发，读 [像素 UI 引擎组件接入指南](./engine/像素UI引擎组件接入指南.md)
6. 如果涉及渲染、性能或 Idle 动画，读 [渲染实现原理](./architecture/渲染实现原理.md)

### 产品和交互设计

1. 读 [产品总规约](./product/产品总规约.md)
2. 再进入对应页面文档

### 当前主线开发

1. 读 [像素 UI 引擎架构与实施计划](./engine/像素UI引擎架构与实施计划.md)
2. 读 [像素 UI 引擎当前进度](./engine/像素UI引擎当前进度.md)
3. 读 [像素 UI 引擎组件接入指南](./engine/像素UI引擎组件接入指南.md)
4. 再看 `pixel-demo` 和对应 runtime 代码

## 4. 文档维护规则

- 技术真实实现变化，先更新 `architecture/` 和 `engine/`
- 产品目标或模块职责变化，更新 `product/`
- 不再新增临时任务单或一次性完成度清单；当前阶段统一维护 [像素 UI 引擎架构与实施计划](./engine/像素UI引擎架构与实施计划.md) 作为执行口径
- 代码路径引用统一使用当前真实模块路径，例如 `app/src/main/kotlin/...`、`pixel-core/src/main/kotlin/...`、`pixel-ui/src/main/kotlin/...`
