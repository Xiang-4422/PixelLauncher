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
tester.enterText(find.byKey("field"), "hello")
tester.composeText(find.byKey("field"), "拼")
tester.submitTextInput()
tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.COPY)
tester.performTextEditAction(find.byKey("field"), PixelTextEditAction.PASTE, pasteText = "text")
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
- `tap` / `doubleTap` / `drag` 先通过 finder 定位 render target bounds，再按坐标命中导出的 click / list / pager / text input / slider target；TextField double tap 会选中单词，非空 selection 的拖动会按最近 handle 更新选区，list 到边界且同点存在 pager target 时会 handoff 给 pager。
- `dragSelectionStartHandle` / `dragSelectionEndHandle` 显式驱动 TextField 选区两端的最小 handle，用于覆盖 selection handle 交互回归。
- `enterText` / `composeText` / `updateComposition` / `submitTextInput` 走 text input target 和 controller；readOnly target 会直接报错。
- `performTextEditAction` 覆盖 Copy/Cut/Paste/Select all，并用 tester 内的
  `clipboardText` 模拟 clipboard；Cut/Paste 会触发 `onChanged`。
- finder 支持多结果和 `nth(index)`；失败时会 dump finder 候选、widget 路径、element tree、render tree 和当前 targets。
- `pumpAndSettle` 会推进 manual scheduler、active ticker、pending build 以及 list/pager scroll activity。

## 后续

- 用 DSL 继续替换旧的 controller 直驱回归测试。
