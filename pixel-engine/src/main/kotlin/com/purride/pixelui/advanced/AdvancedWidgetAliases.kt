package com.purride.pixelui.advanced

import com.purride.pixelui.internal.MultiChildRenderObject as InternalMultiChildRenderObject
import com.purride.pixelui.internal.MultiChildRenderObjectWidget as InternalMultiChildRenderObjectWidget
import com.purride.pixelui.internal.RenderBox as InternalRenderBox
import com.purride.pixelui.internal.RenderObject as InternalRenderObject
import com.purride.pixelui.internal.RenderObjectWidget as InternalRenderObjectWidget
import com.purride.pixelui.internal.RenderObjectWithChild as InternalRenderObjectWithChild
import com.purride.pixelui.internal.RenderObjectWithChildren as InternalRenderObjectWithChildren
import com.purride.pixelui.internal.SingleChildRenderObject as InternalSingleChildRenderObject
import com.purride.pixelui.internal.SingleChildRenderObjectWidget as InternalSingleChildRenderObjectWidget

/**
 * pixel-engine 公开的"高级扩展点"——自定义 RenderObject 用 API。
 *
 * 普通页面层只需要 `com.purride.pixelui.widgets.*` 里的函数式 API
 * （`Text` / `Padding` / `Container` / `ListView` / 等）。当现有 widget
 * 满足不了需求，需要直接定制 layout / paint / hitTest 时，再来用这层。
 *
 * 设计基于 Flutter 的三层模型：
 * - [PixelRenderObjectWidget] —— 不可变 widget 配置
 * - 内部 retained Element 树（不暴露）—— 持有生命周期
 * - [PixelRenderObject] 及子类 —— 真正的 layout / paint / hitTest
 *
 * 三个 widget 基类按 child 数量分：
 * - [PixelLeafRenderObjectWidget] —— 没有 child 的叶节点（如自定义图标）
 * - [PixelSingleChildRenderObjectWidget] —— 1 个 child（如自定义包装器）
 * - [PixelMultiChildRenderObjectWidget] —— 多个 child（如自定义布局容器）
 *
 * 对应 RenderObject 基类：
 * - [PixelRenderBox] —— 通用盒模型基类
 * - [PixelSingleChildRenderObject] —— 单 child 盒模型（实现 [PixelRenderObjectWithChild]）
 * - [PixelMultiChildRenderObject] —— 多 child 盒模型（实现 [PixelRenderObjectWithChildren]）
 *
 * 自定义示例：
 * ```kotlin
 * class MyDotWidget(val color: PixelTone) : PixelLeafRenderObjectWidget() {
 *     override fun createRenderObject(context: BuildContext) = MyDotRender(color)
 *     override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
 *         (renderObject as MyDotRender).updateColor(color)
 *     }
 * }
 *
 * class MyDotRender(private var color: PixelTone) : PixelRenderBox() {
 *     override fun layout(constraints: PixelRenderConstraints) {
 *         size = PixelRenderSize(width = constraints.constrainWidth(4), height = constraints.constrainHeight(4))
 *     }
 *     override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
 *         for (y in 0 until size.height) for (x in 0 until size.width) {
 *             context.buffer.setPixel(offsetX + x, offsetY + y, color.value)
 *         }
 *     }
 *     fun updateColor(next: PixelTone) {
 *         if (next == color) return
 *         color = next
 *         markNeedsPaint()
 *     }
 * }
 * ```
 */

// ---------------- Widget 基类 ----------------

/**
 * 通用 render object widget 基类，对应 Flutter 的 RenderObjectWidget。
 *
 * 不建议直接继承本类；除非确实只画一个不含 child 的叶节点（建议用更
 * 具体的 [PixelLeafRenderObjectWidget] 表达意图）。
 */
public typealias PixelRenderObjectWidget = InternalRenderObjectWidget

/**
 * 不含 child 的叶 render object widget。语义等价于 Flutter 的
 * LeafRenderObjectWidget。
 *
 * 当前实现复用了 [PixelRenderObjectWidget]，只在命名上做了语义标记，
 * 让自定义"叶节点"意图更明确。
 */
public typealias PixelLeafRenderObjectWidget = InternalRenderObjectWidget

/**
 * 单 child render object widget 基类。对应 Flutter 的
 * SingleChildRenderObjectWidget。
 */
public typealias PixelSingleChildRenderObjectWidget = InternalSingleChildRenderObjectWidget

/**
 * 多 child render object widget 基类。对应 Flutter 的
 * MultiChildRenderObjectWidget。
 */
public typealias PixelMultiChildRenderObjectWidget = InternalMultiChildRenderObjectWidget

// ---------------- RenderObject 基类 ----------------

/**
 * 自定义渲染对象的根基类。继承本类（或 [PixelRenderBox]）即可参与
 * pixel-engine 的 layout / paint / hitTest 管线。
 */
public typealias PixelRenderObject = InternalRenderObject

/**
 * 自定义渲染对象的盒模型基类。提供 `size: PixelRenderSize` 字段和
 * `layout(constraints)` / `paint(context, offsetX, offsetY)` / `hitTest(...)`
 * 等入口。
 */
public typealias PixelRenderBox = InternalRenderBox

/**
 * 单 child render object 基类。配合 [PixelSingleChildRenderObjectWidget] 使用。
 */
public typealias PixelSingleChildRenderObject = InternalSingleChildRenderObject

/**
 * 多 child render object 基类。配合 [PixelMultiChildRenderObjectWidget] 使用。
 */
public typealias PixelMultiChildRenderObject = InternalMultiChildRenderObject

// ---------------- 几何/上下文 类型 ----------------

/**
 * 布局阶段父节点下发的约束（minWidth/maxWidth/minHeight/maxHeight）。
 */
public typealias PixelRenderConstraints = com.purride.pixelui.internal.RenderConstraints

/**
 * 布局完成后 render object 自身的尺寸。
 */
public typealias PixelRenderSize = com.purride.pixelui.internal.RenderSize

/**
 * paint 阶段传入的上下文，含目标 buffer 与共享 buffer pool。
 */
public typealias PixelPaintContext = com.purride.pixelui.internal.PaintContext

/**
 * hitTest 收集的命中节点序列。
 */
public typealias PixelHitTestResult = com.purride.pixelui.internal.HitTestResult

// ---------------- 协议接口 ----------------

/**
 * 单 child 协议——render object 需要实现这个接口才能挂接 single child widget。
 */
public typealias PixelRenderObjectWithChild = InternalRenderObjectWithChild

/**
 * 多 child 协议——render object 需要实现这个接口才能挂接 multi-child widget。
 */
public typealias PixelRenderObjectWithChildren = InternalRenderObjectWithChildren
