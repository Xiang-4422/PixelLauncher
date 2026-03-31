package com.purride.pixellauncherv2.render

/**
 * 兼容层别名。
 *
 * `PixelBuffer` 的真实实现已经迁到 `:pixel-core`，
 * 当前先保留旧包名，避免第一轮模块拆分就改动大量渲染调用点。
 */
typealias PixelBuffer = com.purride.pixelcore.PixelBuffer
