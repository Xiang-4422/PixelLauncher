package com.purride.pixellauncherv2.render

/**
 * 兼容层别名。
 *
 * `PixelGridGeometry` 的真实定义已经迁到 `:pixel-core`，
 * 当前保留旧包名，避免第一轮拆分改动过大。
 */
typealias PixelGridGeometry = com.purride.pixelcore.PixelGridGeometry

/**
 * 兼容层别名。
 *
 * `PixelGridGeometryResolver` 的真实实现已经迁到 `:pixel-core`，
 * 当前保留旧入口给现有显示实现继续使用。
 */
typealias PixelGridGeometryResolver = com.purride.pixelcore.PixelGridGeometryResolver
