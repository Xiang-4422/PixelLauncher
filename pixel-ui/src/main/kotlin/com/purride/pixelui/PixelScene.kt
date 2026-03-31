package com.purride.pixelui

/**
 * 像素页面的声明式入口。
 *
 * 业务层输入状态，返回一棵用于布局、绘制和交互分发的组件树。
 * 第一版先采用显式状态输入，不引入隐式 remember 或编译器增强能力。
 */
fun interface PixelScene<State> {
    fun render(state: State): PixelNode
}
