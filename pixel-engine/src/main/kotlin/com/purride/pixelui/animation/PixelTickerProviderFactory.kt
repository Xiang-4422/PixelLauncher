package com.purride.pixelui.animation

import com.purride.pixelui.host.PixelFrameScheduler

/** 为每个 Host 创建独立 ticker provider 的可注入工厂。 */
public fun interface PixelTickerProviderFactory {
    /** 使用当前 Host 私有调度边界创建 ticker provider。 */
    public fun create(frameScheduler: PixelFrameScheduler): PixelTickerProvider

    /** 集中提供 `PixelTickerProviderFactory` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 默认工厂，每次调用都会创建互不共享状态的新 provider。 */
        public val Default: PixelTickerProviderFactory =
            PixelTickerProviderFactory(::PixelTickerProvider)
    }
}
