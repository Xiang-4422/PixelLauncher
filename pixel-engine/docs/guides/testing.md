# 测试

普通组件测试使用 `pixel-testing` 的 `PixelTester`，不需要 Android View。固定 logical size、内置字体、
手动 scheduler 和主题 profile 后，测试可以确定性驱动点击、拖动、键盘、IME、动画、像素和 semantics。

```kotlin
val tester = PixelTester()
tester.pumpWidget(AppRoot(), logicalWidth = 128, logicalHeight = 64)
tester.tap(find.byText("OPEN"))
tester.pumpAndSettle()
check(tester.dumpSemanticsTree().contains("DIALOG"))
tester.dispose()
```

分层建议：

- JVM：状态机、布局、像素、semantics、固定时钟动画和属性测试。
- instrumentation：真实 Host、View 输入、IME、Accessibility、Lifecycle、Insets、Back、SavedState。
- golden：源码 baseline 只经人工 patch 更新；失败只写 build candidate/diff。
- consumer：从隔离 file-Maven 解析发布坐标并构建 R8 release；不能 project dependency 偷渡。
- 性能：功能测试不能替代 micro/macrobenchmark 和 soak。

仓库门禁入口与报告位置见 [性能指南](performance.md) 和
[CI 验收记录](../acceptance/m8-3-ci-quality-gates-2026-07-14.md)。
