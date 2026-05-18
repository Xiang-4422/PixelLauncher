package com.purride.pixelcore

/**
 * 像素显示内核分层入口。
 *
 * 这个 package 只承载与业务无关的像素基础设施：
 * 像素缓冲、调色板、显示几何、帧交换、字体底座，以及轴向位移与合成原语。
 *
 * 它不负责任何产品页面语义，也不应该知道 HOME、DRAWER、SMS、IDLE 这类概念。
 */
public object PixelCoreModule
