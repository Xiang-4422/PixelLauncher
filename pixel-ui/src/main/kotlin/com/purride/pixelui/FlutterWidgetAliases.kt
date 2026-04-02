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
typealias ContainerStyle = PixelContainerStyle
typealias TextFieldStyle = PixelTextFieldStyle
typealias TextOverflow = PixelTextOverflow
typealias TextInputAction = PixelTextInputAction
typealias ThemeData = PixelThemeData

private fun Widget.asPixelNode(): PixelNode {
    return this as? PixelNode
        ?: error("当前阶段 Flutter 风格公开组件仍需映射到 PixelNode 兼容层，收到未兼容的 Widget 类型: ${this::class.qualifiedName}")
}

private fun PixelAlignment.toAlignment(): Alignment {
    return when (this) {
        PixelAlignment.TOP_START -> Alignment.TOP_START
        PixelAlignment.CENTER -> Alignment.CENTER
    }
}

private fun PixelSurfaceNode.matchesThemeStyle(style: ContainerStyle): Boolean {
    return fillTone == style.fillTone &&
        borderTone == style.borderTone &&
        alignment.toAlignment() == style.alignment
}

private fun applyThemeToNode(
    node: PixelNode,
    theme: ThemeData,
): PixelNode {
    return when (node) {
        is PixelTextNode -> {
            val resolvedStyle = if (node.styleLocked) {
                node.style
            } else {
                when (node.style) {
                    TextStyle.Default -> theme.textStyle
                    TextStyle.Accent -> theme.accentTextStyle
                    else -> node.style
                }
            }
            node.copy(
                style = resolvedStyle,
                styleLocked = true,
            )
        }

        is PixelButtonNode -> {
            val resolvedStyle = if (node.styleLocked) {
                node.style
            } else {
                when (node.style) {
                    ButtonStyle.Default -> theme.buttonStyle
                    ButtonStyle.Accent -> theme.accentButtonStyle
                    else -> node.style
                }
            }
            val resolvedDisabledStyle = if (node.styleLocked) {
                node.disabledStyle
            } else {
                theme.disabledButtonStyle
            }
            node.copy(
                style = resolvedStyle,
                disabledStyle = resolvedDisabledStyle,
                styleLocked = true,
            )
        }

        is PixelTextFieldNode -> {
            val resolvedStyle = if (node.styleLocked) {
                node.style
            } else {
                when {
                    node.style != TextFieldStyle.Default -> node.style
                    !node.enabled -> theme.disabledTextFieldStyle
                    node.readOnly -> theme.readOnlyTextFieldStyle
                    else -> theme.textFieldStyle
                }
            }
            node.copy(
                style = resolvedStyle,
                styleLocked = true,
            )
        }

        is PixelSurfaceNode -> {
            val resolvedStyle = if (node.styleLocked) {
                null
            } else {
                when {
                    node.matchesThemeStyle(ContainerStyle.Default) -> theme.containerStyle
                    node.matchesThemeStyle(theme.accentContainerStyle) -> theme.accentContainerStyle
                    node.matchesThemeStyle(
                        ContainerStyle(
                            fillTone = PixelTone.OFF,
                            borderTone = PixelTone.ACCENT,
                            alignment = Alignment.CENTER,
                        ),
                    ) -> theme.accentContainerStyle
                    else -> null
                }
            }
            node.copy(
                child = node.child?.let { applyThemeToNode(it, theme) },
                fillTone = resolvedStyle?.fillTone ?: node.fillTone,
                borderTone = resolvedStyle?.borderTone ?: node.borderTone,
                alignment = resolvedStyle?.alignment?.toPixelAlignment() ?: node.alignment,
                styleLocked = true,
            )
        }

        is PixelBoxNode -> node.copy(children = node.children.map { applyThemeToNode(it, theme) })
        is PixelRowNode -> node.copy(children = node.children.map { applyThemeToNode(it, theme) })
        is PixelColumnNode -> node.copy(children = node.children.map { applyThemeToNode(it, theme) })
        is PixelPagerNode -> node.copy(pages = node.pages.map { applyThemeToNode(it, theme) })
        is PixelListNode -> node.copy(items = node.items.map { applyThemeToNode(it, theme) })
        is PixelSingleChildScrollViewNode -> node.copy(child = applyThemeToNode(node.child, theme))
        else -> node
    }
}

fun Theme(
    data: ThemeData,
    child: Widget,
): Widget {
    return applyThemeToNode(
        node = child.asPixelNode(),
        theme = data,
    )
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
    theme: ThemeData? = null,
    softWrap: Boolean = false,
    maxLines: Int = 1,
    overflow: PixelTextOverflow = PixelTextOverflow.CLIP,
    key: Any? = null,
): Widget {
    val resolvedStyle = when {
        theme == null -> style
        style == TextStyle.Default -> theme.textStyle
        style == TextStyle.Accent -> theme.accentTextStyle
        else -> style
    }
    return PixelText(
        text = data,
        modifier = modifier,
        style = resolvedStyle,
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
    style: ContainerStyle? = null,
    theme: ThemeData? = null,
    modifier: PixelModifier = PixelModifier.Empty,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    val resolvedStyle = style ?: theme?.let {
        val defaultContainer = ContainerStyle(
            fillTone = fillTone,
            borderTone = borderTone,
            alignment = alignment,
        )
        if (defaultContainer == ContainerStyle.Default) {
            it.containerStyle
        } else if (defaultContainer == it.accentContainerStyle) {
            it.accentContainerStyle
        } else {
            defaultContainer
        }
    } ?: ContainerStyle(
        fillTone = fillTone,
        borderTone = borderTone,
        alignment = alignment,
    )
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
        fillTone = resolvedStyle.fillTone,
        borderTone = resolvedStyle.borderTone,
        padding = 0,
        alignment = resolvedStyle.alignment,
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
    theme: ThemeData? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    autofocus: Boolean = false,
    textInputAction: TextInputAction = TextInputAction.DONE,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    key: Any? = null,
): Widget {
    val resolvedStyle = when {
        theme == null -> style
        style != TextFieldStyle.Default -> style
        !enabled -> theme.disabledTextFieldStyle
        readOnly -> theme.readOnlyTextFieldStyle
        else -> theme.textFieldStyle
    }
    return PixelTextField(
        state = state,
        controller = controller,
        modifier = modifier,
        placeholder = placeholder,
        style = resolvedStyle,
        enabled = enabled,
        readOnly = readOnly,
        autofocus = autofocus,
        textInputAction = textInputAction,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
        key = key,
    )
}

fun OutlinedButton(
    text: String,
    onPressed: (() -> Unit)?,
    modifier: PixelModifier = PixelModifier.Empty,
    style: ButtonStyle = ButtonStyle.Default,
    theme: ThemeData? = null,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    val resolvedStyle = when {
        theme == null -> style
        style == ButtonStyle.Default -> theme.buttonStyle
        style == ButtonStyle.Accent -> theme.accentButtonStyle
        else -> style
    }
    val resolvedDisabledStyle = theme?.disabledButtonStyle ?: PixelButtonStyle.Disabled
    return PixelButton(
        text = text,
        onClick = onPressed,
        modifier = modifier,
        style = resolvedStyle,
        disabledStyle = resolvedDisabledStyle,
        enabled = enabled,
        key = key,
    )
}
