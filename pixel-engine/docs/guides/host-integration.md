# Android Host 接入

一个 Activity 或 Fragment View owner 持有一个 `PixelHostSetup`。需要注入 clock、scheduler、资源或错误上报时，先构造不可变 `PixelEngine`，再创建 Host。

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

- Activity 在 `onDestroy` 调用 `setup.dispose()`。
- Fragment 在 `onDestroyView` 调用 `setup.dispose()`。
- detach 只暂停 retained tree，dispose 才释放 runtime、焦点、IME 和帧回调。
- 不要在全局单例保存 `PixelHostView`。

## Back、Insets 与恢复

`PixelHostSetupConfig.backDispatcher` 先给 Overlay、焦点和路由处理，未消费时再调用 `onUnhandledBack`。WindowInsets 会映射为逻辑像素 `MediaQuery.viewPadding/viewInsets`；页面使用 `SafeArea` 和 `ImeAvoidingView`，不要重复扣除系统栏。

当前不发布 Compose wrapper。Compose 应用可以在自身代码中使用 `AndroidView` 承载 `setup.rootView`，并遵循相同的 dispose 规则。
