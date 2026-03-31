package com.purride.pixellauncherv2.render

/**
 * 兼容层别名。
 *
 * `PixelTheme` 的真实定义已经迁到 `:pixel-core`，
 * 当前保留旧包名，避免第一轮拆分改动过大。
 */
typealias PixelTheme = com.purride.pixelcore.PixelTheme

/**
 * 兼容层别名。
 *
 * `PixelPalette` 的真实实现已经迁到 `:pixel-core`，
 * 当前保留旧包名入口，先保证现有渲染链保持稳定。
 */
typealias PixelPalette = com.purride.pixelcore.PixelPalette
