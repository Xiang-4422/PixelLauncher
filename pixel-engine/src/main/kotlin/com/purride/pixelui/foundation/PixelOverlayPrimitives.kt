package com.purride.pixelui

/**
 * Overlay 的标准绘制层。
 *
 * 数值越大越接近用户；同层内由 controller 的插入顺序决定前后。该协议由 runtime 持有，
 * 具体 Overlay Host 与标准表面由 widgets artifact 实现。
 */
public enum class PixelOverlayLayer {
    /** 普通浮层，例如无模态的自定义 popup。 */
    Popup,

    /** 短暂通知，例如 toast 和 snackbar。 */
    Notification,

    /** 需要隔离背景交互和焦点的模态内容。 */
    Modal,

    /** SDK 或宿主保留的最上层系统呈现。 */
    System,
}

/**
 * 碰撞感知 [Popover] 的垂直放置策略。
 *
 * 该枚举属于 Overlay 布局协议而不是具体组件实现，因此由 runtime artifact 持有；
 * 包名保持不变，以继续兼容已经编译的消费者。
 */
public enum class PixelPopoverPlacement {
    /** 优先使用旧版向下偏移位置，空间不足时翻转到锚点上方。 */
    Auto,

    /** 优先放在锚点下方，仅在视口更小时执行边界钳制。 */
    Below,

    /** 优先放在锚点上方，再执行视口边界钳制。 */
    Above,
}

/**
 * 根据锚点全局测量边界解析的水平对齐方式。
 *
 * 该值会同时影响布局、绘制、命中测试和语义边界，调用方不应把它当作仅视觉属性。
 */
public enum class PixelPopoverAlignment {
    /** 将弹层起始边与锚点起始边对齐。 */
    Start,

    /** 将弹层相对锚点水平居中。 */
    Center,

    /** 将弹层结束边与锚点结束边对齐。 */
    End,
}
