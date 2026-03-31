package com.purride.pixellauncherv2.render

/**
 * 兼容层别名。
 *
 * `PixelFrameView` 的真实契约已经迁到 `:pixel-core`，
 * 当前先保留旧包名，避免第一轮拆分时大面积修改实现类和调用方。
 */
typealias PixelFrameView = com.purride.pixelcore.PixelFrameView
