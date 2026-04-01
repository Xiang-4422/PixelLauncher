package com.purride.pixelui

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

/**
 * Flutter 风格公开别名层。
 *
 * 当前阶段先把外部使用语言统一为 Flutter 风格，
 * 底层仍然复用现有 `Pixel*` 组件实现，保证演进成本可控。
 */

typealias TextStyle = PixelTextStyle
typealias ButtonStyle = PixelButtonStyle
typealias TextFieldStyle = PixelTextFieldStyle
typealias TextOverflow = PixelTextOverflow

private fun Widget.asPixelNode(): PixelNode {
    return this as? PixelNode
        ?: error("当前阶段 Flutter 风格公开组件仍需映射到 PixelNode 兼容层，收到未兼容的 Widget 类型: ${this::class.qualifiedName}")
}

/**
 * Flutter 风格的 `Padding` 包装组件。
 *
 * 当前阶段直接复用兼容层的 `PixelModifier.padding(...)`，
 * 先把公开 API 拉到 Flutter 风格，底层布局语义保持不变。
 */
fun Padding(
    child: Widget,
    all: Int,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return Stack(
        children = listOf(child),
        modifier = modifier.padding(all),
        alignment = Alignment.TOP_START,
        key = key,
    )
}

fun Padding(
    child: Widget,
    padding: EdgeInsets,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return Stack(
        children = listOf(child),
        modifier = modifier.padding(
            left = padding.left,
            top = padding.top,
            right = padding.right,
            bottom = padding.bottom,
        ),
        alignment = Alignment.TOP_START,
        key = key,
    )
}

fun Padding(
    child: Widget,
    horizontal: Int = 0,
    vertical: Int = 0,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return Stack(
        children = listOf(child),
        modifier = modifier.padding(horizontal = horizontal, vertical = vertical),
        alignment = Alignment.TOP_START,
        key = key,
    )
}

fun Padding(
    child: Widget,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return Stack(
        children = listOf(child),
        modifier = modifier.padding(left = left, top = top, right = right, bottom = bottom),
        alignment = Alignment.TOP_START,
        key = key,
    )
}

fun Align(
    child: Widget,
    alignment: Alignment = Alignment.CENTER,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return Stack(
        children = listOf(child),
        modifier = modifier.fillMaxSize(),
        alignment = alignment,
        key = key,
    )
}

/**
 * 当前阶段先提供最常用的 `Center`。
 */
fun Center(
    child: Widget,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return Align(
        child = child,
        alignment = Alignment.CENTER,
        modifier = modifier,
        key = key,
    )
}

/**
 * Flutter 风格的 `SizedBox`。
 *
 * 当前既支持空盒子，也支持包裹单个 child 的固定尺寸容器。
 */
fun SizedBox(
    width: Int? = null,
    height: Int? = null,
    child: Widget? = null,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    val sizedModifier = when {
        width != null && height != null -> modifier.size(width, height)
        width != null -> modifier.width(width)
        height != null -> modifier.height(height)
        else -> modifier
    }
    return Stack(
        children = child?.let { listOf(it) } ?: emptyList(),
        modifier = sizedModifier,
        alignment = Alignment.TOP_START,
        key = key,
    )
}

/**
 * `Flexible/Expanded/Spacer` 当前仍然复用兼容层的 `weight` 语义。
 *
 * 这一步先把公开组件名称和分类拉正，后续再把“松/紧约束”语义补齐。
 */
fun Flexible(
    child: Widget,
    flex: Int = 1,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return Stack(
        children = listOf(child),
        modifier = modifier.weight(flex.coerceAtLeast(0).toFloat()),
        alignment = Alignment.TOP_START,
        key = key,
    )
}

fun Expanded(
    child: Widget,
    flex: Int = 1,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return Flexible(
        child = child,
        flex = flex,
        modifier = modifier,
        key = key,
    )
}

fun Spacer(
    flex: Int = 1,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return SizedBox(
        modifier = modifier.weight(flex.coerceAtLeast(0).toFloat()),
        key = key,
    )
}

fun GestureDetector(
    child: Widget,
    onTap: () -> Unit,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return Stack(
        children = listOf(child),
        modifier = modifier.clickable(onTap),
        alignment = Alignment.TOP_START,
        key = key,
    )
}

fun Text(
    data: String,
    modifier: PixelModifier = PixelModifier.Empty,
    style: TextStyle = TextStyle.Default,
    softWrap: Boolean = false,
    maxLines: Int = 1,
    overflow: PixelTextOverflow = PixelTextOverflow.CLIP,
    key: Any? = null,
): Widget {
    return PixelText(
        text = data,
        modifier = modifier,
        style = style,
        softWrap = softWrap,
        maxLines = maxLines,
        overflow = overflow,
        key = key,
    )
}

fun DecoratedBox(
    child: Widget? = null,
    modifier: PixelModifier = PixelModifier.Empty,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    padding: Int = 2,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return PixelSurface(
        child = child?.asPixelNode(),
        modifier = modifier,
        fillTone = fillTone,
        borderTone = borderTone,
        padding = padding,
        alignment = alignment.toPixelAlignment(),
        key = key,
    )
}

fun Container(
    child: Widget? = null,
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    margin: EdgeInsets? = null,
    modifier: PixelModifier = PixelModifier.Empty,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    val sizedModifier = when {
        width != null && height != null -> modifier.size(width, height)
        width != null -> modifier.width(width)
        height != null -> modifier.height(height)
        else -> modifier
    }
    val decoratedChild = child?.let {
        if (padding != null) {
            Padding(child = it, padding = padding)
        } else {
            it
        }
    }
    val decoratedBox = DecoratedBox(
        child = decoratedChild,
        modifier = sizedModifier,
        fillTone = fillTone,
        borderTone = borderTone,
        padding = 0,
        alignment = alignment,
        key = key,
    )
    return if (margin != null) {
        Padding(
            child = decoratedBox,
            padding = margin,
        )
    } else {
        decoratedBox
    }
}

fun Stack(
    children: List<Widget>,
    modifier: PixelModifier = PixelModifier.Empty,
    alignment: Alignment = Alignment.TOP_START,
    key: Any? = null,
): Widget {
    return PixelBox(
        children = children.map { it.asPixelNode() },
        modifier = modifier,
        alignment = alignment.toPixelAlignment(),
        key = key,
    )
}

fun Row(
    children: List<Widget>,
    modifier: PixelModifier = PixelModifier.Empty,
    spacing: Int = 0,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.START,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.START,
    key: Any? = null,
): Widget {
    return PixelRow(
        children = children.map { it.asPixelNode() },
        modifier = modifier,
        spacing = spacing,
        mainAxisAlignment = mainAxisAlignment.toPixelMainAxisAlignment(),
        crossAxisAlignment = crossAxisAlignment.toPixelCrossAxisAlignment(),
        key = key,
    )
}

fun Column(
    children: List<Widget>,
    modifier: PixelModifier = PixelModifier.Empty,
    spacing: Int = 0,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.START,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.START,
    key: Any? = null,
): Widget {
    return PixelColumn(
        children = children.map { it.asPixelNode() },
        modifier = modifier,
        spacing = spacing,
        mainAxisAlignment = mainAxisAlignment.toPixelMainAxisAlignment(),
        crossAxisAlignment = crossAxisAlignment.toPixelCrossAxisAlignment(),
        key = key,
    )
}

fun PageView(
    axis: Axis,
    controller: PixelPagerController,
    state: PixelPagerState,
    pages: List<Widget>,
    modifier: PixelModifier = PixelModifier.Empty,
    onPageChanged: ((Int) -> Unit)? = null,
    key: Any? = null,
): Widget {
    return PixelPager(
        axis = axis,
        state = state,
        controller = controller,
        pages = pages.map { it.asPixelNode() },
        modifier = modifier,
        onPageChanged = onPageChanged,
        key = key,
    )
}

fun PageViewBuilder(
    axis: Axis,
    controller: PixelPagerController,
    state: PixelPagerState,
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    modifier: PixelModifier = PixelModifier.Empty,
    onPageChanged: ((Int) -> Unit)? = null,
    key: Any? = null,
): Widget {
    return PageView(
        axis = axis,
        controller = controller,
        state = state,
        pages = List(itemCount) { index -> itemBuilder(index) },
        modifier = modifier,
        onPageChanged = onPageChanged,
        key = key,
    )
}

fun ListView(
    items: List<Widget>,
    state: PixelListState,
    controller: PixelListController,
    modifier: PixelModifier = PixelModifier.Empty,
    spacing: Int = 0,
    key: Any? = null,
): Widget {
    return PixelList(
        items = items.map { it.asPixelNode() },
        state = state,
        controller = controller,
        modifier = modifier,
        spacing = spacing,
        key = key,
    )
}

fun ListViewBuilder(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    state: PixelListState,
    controller: PixelListController,
    modifier: PixelModifier = PixelModifier.Empty,
    spacing: Int = 0,
    key: Any? = null,
): Widget {
    return ListView(
        items = List(itemCount) { index -> itemBuilder(index) },
        state = state,
        controller = controller,
        modifier = modifier,
        spacing = spacing,
        key = key,
    )
}

fun ListViewSeparated(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    separatorBuilder: (Int) -> Widget,
    state: PixelListState,
    controller: PixelListController,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    val separatedItems = buildList {
        repeat(itemCount) { index ->
            add(itemBuilder(index))
            if (index < itemCount - 1) {
                add(separatorBuilder(index))
            }
        }
    }
    return ListView(
        items = separatedItems,
        state = state,
        controller = controller,
        modifier = modifier,
        spacing = 0,
        key = key,
    )
}

fun SingleChildScrollView(
    child: Widget,
    state: PixelListState,
    controller: PixelListController,
    modifier: PixelModifier = PixelModifier.Empty,
    key: Any? = null,
): Widget {
    return PixelSingleChildScrollView(
        child = child.asPixelNode(),
        state = state,
        controller = controller,
        modifier = modifier,
        key = key,
    )
}

fun TextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
    modifier: PixelModifier = PixelModifier.Empty,
    placeholder: String = "",
    style: TextFieldStyle = TextFieldStyle.Default,
    enabled: Boolean = true,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    key: Any? = null,
): Widget {
    return PixelTextField(
        state = state,
        controller = controller,
        modifier = modifier,
        placeholder = placeholder,
        style = style,
        enabled = enabled,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
        key = key,
    )
}

fun OutlinedButton(
    text: String,
    onPressed: () -> Unit,
    modifier: PixelModifier = PixelModifier.Empty,
    style: ButtonStyle = ButtonStyle.Default,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    return PixelButton(
        text = text,
        onClick = onPressed,
        modifier = modifier,
        style = style,
        enabled = enabled,
        key = key,
    )
}
