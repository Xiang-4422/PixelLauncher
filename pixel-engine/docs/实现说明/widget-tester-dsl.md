# WidgetTester DSL

`com.purride.pixelui.testing` 是 test-only DSL，只存在于 `pixel-engine/src/test`，
不会进入 release AAR。目标是在 JVM 单测里直接驱动 retained runtime 和 render
pipeline，不依赖 Android `View`。

## 当前能力

```kotlin
val tester = PixelTester()

tester.pumpWidget(
    widget = OutlinedButton(text = "OK", onPressed = { clicked = true }),
    logicalWidth = 32,
    logicalHeight = 12,
)

tester.tap(find.byText("OK"))
tester.doubleTap(find.byKey("field"))
tester.dragSelectionEndHandle(find.byKey("field"), dx = 8, dy = 0)
tester.drag(find.byKey("list"), dx = 0, dy = -12)
tester.fling(find.byKey("list"), dx = 0, dy = -4, velocityPxPerSecond = -120f)
tester.cancelDrag(find.byKey("pager"), dx = -12, dy = 0)
tester.startGesture(find.byKey("pager"))
    .moveBy(dx = -12, dy = 0)
    .cancel()
tester.enterText(find.byKey("field"), "hello")
tester.composeText(find.byKey("field"), "拼")
tester.submitTextInput()
tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.COPY)
tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.PASTE, pasteText = "text")
tester.exists(find.byText("READY"))
tester.pumpFrame(deltaMs = 16)
tester.pumpAndSettle(maxFrames = 60)
```

Finder：

- `find.byText("OK")`
- `find.byType(SomeWidget::class)`
- `find.byKey("key")`
- `find.byText("OK").nth(1)`

## 边界

- `pumpWidget` 直接调用 `PixelUiRuntime.render`。
- `tap` / `doubleTap` / `drag` / `fling` / `cancelDrag` 先通过 finder 定位 render target bounds，再按坐标命中导出的 click / list / pager / text input / slider target；TextField double tap 会选中单词，非空 selection 的拖动会按最近 handle 更新选区，list 到边界且同点存在 pager target 时会 handoff 给 pager。
- `fling` 会把调用方提供的 logical velocity 传给 list / pager controller 的 `endDrag`；`cancelDrag` 覆盖 host cancel 分支，list 以 0 velocity 结束拖动，pager 调用 `cancelDrag` 回弹到当前页。
- `startGesture(finder, pointerId)` 返回 `PixelTestGesture`，可逐步调用 `moveBy` / `up` / `cancel`。该路径保留 down 后、move 中、up/cancel 后的中间态，适合断言 list/pager dragging、点击只在 up 后提交，以及不同 `pointerId` 的测试输入流互不覆盖。
- `dragSelectionStartHandle` / `dragSelectionEndHandle` 显式驱动 TextField 选区两端的最小 handle，用于覆盖 selection handle 交互回归。
- `enterText` / `composeText` / `updateComposition` / `submitTextInput` 走 text input target 和 controller；readOnly target 会直接报错。
- `performTextEditAction` 覆盖 Copy/Cut/Paste/Select all，并用 tester 内的
  `clipboardText` 模拟 clipboard；Cut/Paste 会触发 `onChanged`。
- finder 支持多结果和 `nth(index)`；失败时会 dump finder 候选、widget 路径、element tree、render tree 和当前 targets。
- `exists(finder)` 只做 finder 匹配，不要求目标可点击，适合断言 overlay / debug 文本。
- `pumpAndSettle` 会推进 manual scheduler、active ticker、pending build 以及 list/pager scroll activity。

## 后续

- 用 DSL 继续替换旧的 controller 直驱回归测试。
- gesture stream 已支持 pointerId、primary pointer handoff，以及基于 move sample 的 list / pager velocity 估算；`moveBy(dx, dy, deltaMs)` 可显式控制测试时间步长。
