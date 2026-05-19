# pixel-engine Architecture

## What is pixel-engine?

pixel-engine is a Flutter-style pixel-art UI SDK for Android. Instead of using the Android View system, it renders every pixel itself into a single `SurfaceView`/`Canvas`. Widgets are pure Kotlin data objects; the engine translates them into a retained element tree that drives layout, paint, and hit-test passes on a `PixelBuffer`.

The design philosophy mirrors Flutter almost verbatim: widgets are immutable configuration, elements are the mutable runtime counterparts, and render objects are the low-level geometry/paint nodes.

---

## Three-Layer Model

```
┌─────────────────────────────────────────────────────────┐
│  Widget layer  (immutable configuration objects)        │
│  Row, Column, Text, Padding, Container, PixelButton …   │
│  Lives in: com.purride.pixelui.widgets.*                │
└───────────────────┬─────────────────────────────────────┘
                    │ BuildOwner.updateRootWidget()
                    ▼
┌─────────────────────────────────────────────────────────┐
│  Element layer  (mutable runtime tree)                  │
│  StatefulElement, StatelessElement,                     │
│  SingleChildRenderObjectElement, …                      │
│  Lives in: com.purride.pixelui.internal.retained.*      │
└───────────────────┬─────────────────────────────────────┘
                    │ element.updateRenderObject()
                    ▼
┌─────────────────────────────────────────────────────────┐
│  RenderObject layer  (layout + paint + hit-test)        │
│  RenderBox, RenderText, RenderFlex, RenderStack, …      │
│  Lives in: com.purride.pixelui.internal.render.*        │
└─────────────────────────────────────────────────────────┘
```

### Widget layer

Widgets are `data class`es (or `class`es for stateful widgets) that carry configuration only. Reconstructing a widget is cheap. The engine compares new widgets to old ones via `==` to decide whether a rebuild is needed.

### Element layer

Elements own the widget's lifecycle. A `StatefulElement` holds the `State<T>` object that persists across rebuilds. `BuildOwner` is the scheduler: it collects dirty elements, calls `rebuild()` in a single pass, and notifies the pipeline that paint is needed.

### RenderObject layer

`RenderBox` subclasses implement `layout(constraints)` and `paint(context, offsetX, offsetY)`. The pipeline runs `PipelineOwner.render()`, which walks the tree depth-first: layout first, paint second. Results land in a `PixelBuffer` (a packed `ByteArray` of palette indices).

---

## Retained Build Pipeline

```
PixelUiRuntime.render(newWidget)
        │
        ▼
BuildOwner.updateRootWidget(newWidget)
   ├─ element.update(newWidget)   ← reconciles children recursively
   └─ DirtyElementScheduler      ← collects State-marked dirty elements
        │
        ▼ rebuild dirty elements
BuildOwner.buildScope()
   └─ element.rebuild() → build() → update children
        │
        ▼
PipelineOwner.render()
   ├─ needsLayout? → RenderObject.layout(constraints)
   │     (short-circuits when constraints + RO.== unchanged)
   └─ needsPaint?  → RenderObject.paint(context, offset)
        │
        ▼
PixelRenderResult  →  PixelHostView.onDraw()  →  Canvas.drawBitmap()
```

`PixelUiRuntime` is the single public entry point in the host module. The `PixelHostView` (Android `View` subclass) wraps it, owns a `PixelFrameScheduler`, and calls `invalidate()` on each rendered frame.

---

## Performance Optimizations (Phase 2)

### PixelBufferPool

`PixelBufferPool` (`com.purride.pixelcore.graphics`) maintains a per-size `ConcurrentLinkedQueue<PixelBuffer>`. `acquire(w, h)` reuses a matching buffer; `release(buf)` clears and returns it. Per-frame `ByteArray` allocation drops from ~30 KB to near zero once the pool is warm.

### PipelineOwner frame caching

`PipelineOwner` keeps `needsLayout` and `needsPaint` dirty flags. A subtree skips layout if the incoming `RenderConstraints` matches the cached value and `RenderObject.==` equality holds. A subtree skips paint if nothing beneath it called `markNeedsPaint()`.

### RenderObject equality short-circuit

Every `updateRenderObject` checks new vs. old field values before calling `markNeedsLayout` / `markNeedsPaint`. If nothing changed, neither flag is set, and both passes skip that subtree entirely.

### PixelBuffer.blit arraycopy

`PixelBuffer.blit` uses `System.arraycopy` for horizontally-aligned source/dest regions (one call per row), falling back to the pixel loop only for clipped or scaled cases.

### LRU glyph cache + RichText O(n) line-wrap

`PixelFontEngine` uses a 2 048-entry LRU `LinkedHashMap` keyed on `(codePoint, glyphStyleHash)`. `RenderRichText.wrapCharacters` is a single linear scan with a `currentLineWidth` accumulator — no quadratic re-measurement.

---

## Public API Boundaries

pixel-engine deliberately uses a **mostly-flat namespace** with a handful of dedicated sub-packages for concerns that benefit from grouping. The actual layout:

| Namespace | Contents | Notes |
|---|---|---|
| `com.purride.pixelui` (flat root) | All widgets (`Text`, `Row`, `Column`, `Padding`, `Stack`, `PixelButton`, …), theme types (`PixelThemeData`, `PixelThemeTokens`, `EdgeInsets`, `PixelTextStyle`, …), host types (`PixelHostView`, `PixelHostSetupConfig`, `PixelHostBridge`, …), framework root types (`Widget`, `BuildContext`, `Directionality`, `MediaQuery`, `DefaultTextRasterizer`, …) | Most public API lives here; users do `import com.purride.pixelui.Text` etc. |
| `com.purride.pixelui.state` | Controllers (`PixelListController`, `PixelPagerController`, `PixelTextFieldController`) and their state types | Dedicated because controllers are stateful + listened to |
| `com.purride.pixelui.gesture` | `PagerGesturePolicy`, `NestedScrollGesturePolicy` | Override these to customize gesture recognition |
| `com.purride.pixelui.host` | `PixelFrameScheduler` (interface + `ManualFrameScheduler`) | Note: most host classes are in the flat root; only the frame scheduler is in this sub-package |
| `com.purride.pixelui.advanced` | Extension points: `PixelLeafRenderObjectWidget`, `PixelRenderBox`, `PixelPaintContext`, `PixelRenderConstraints`, `PixelRenderSize`, … | Typealiases re-exporting selected internal types for custom RenderObject authoring |

The source-tree directories `host/`, `theme/`, `widgets/`, `foundation/` exist for code organization but **do not** define sub-packages — files inside them declare `package com.purride.pixelui` (flat). This is intentional: it keeps imports concise for the most common widget-building use cases.

Everything under `com.purride.pixelui.internal.*` is SDK-private. Do not import internal packages — the module is compiled with `explicitApi = Strict`, so internal declarations will not appear in IDE completions for external consumers.
