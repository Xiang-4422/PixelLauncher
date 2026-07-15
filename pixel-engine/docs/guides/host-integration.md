# Android Host 接入

## 推荐边界

一个 Activity/Fragment View owner 持有一个 `PixelHostSetup`。需要注入 clock、scheduler、资源、
错误上报或 Host capability 时，先构造不可变 `PixelEngine`，再调用带 `engine` 的
`createPixelHostSetup` 重载。不同 Engine 默认不共享 cache、焦点或输入状态。

```kotlin
val engine = PixelEngine.Builder()
    .errorReporter(appErrorReporter)
    .logger(appLogger)
    .build()

val setup = createPixelHostSetup(
    context = requireContext(),
    engine = engine,
    config = PixelHostSetupConfig(content = { AppRoot() }),
)
```

## 生命周期

- Activity 在 `onDestroy`、Fragment 在 `onDestroyView` 调用 `setup.dispose()`。
- `onPause`/`onResume` 由默认 Host/Lifecycle bridge 驱动 ticker 和输入；不要手工创建第二套帧循环。
- detach 只暂停 retained tree，dispose 才释放 runtime、焦点 owner、IME 与待处理帧。
- 同一 Activity 多 Host 必须分别持有 setup；不要在全局单例保存 `PixelHostView`。

## Back、Insets 与 Saved State

`PixelHostSetupConfig.backDispatcher` 先给 Overlay/焦点/路由处理，未消费时调用
`onUnhandledBack`。WindowInsets 会转换为逻辑像素 `MediaQuery.viewPadding/viewInsets`；页面用
`SafeArea` 和 `ImeAvoidingView`，不要重复扣除系统栏。路由、列表、分页和文本字段的持久状态应
使用各自 snapshot API，恢复失败必须保留结构化原因，不能半提交。

## Compose

`pixel-compose` 的 `PixelHost` 是单向 `AndroidView` adapter：Compose 承载 Pixel tree，任意
Composable 不能反向嵌入 Pixel render tree。adapter 会传递 Lifecycle、density/configuration、
Insets、焦点、IME、无障碍与有界 saved state。详见
[Compose Host 迁移指南](../migrations/1.0.0-compose-host.md)。
