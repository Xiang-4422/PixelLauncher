package com.purride.pixelengine

/**
 * 旧聚合坐标的二进制兼容 marker。
 *
 * 新代码应通过 [PixelEngine.Builder] 创建实例；该对象只为已编译消费者保留。
 */
@Deprecated("Use PixelEngine.Builder to create an isolated engine instance.")
public object PixelEngineModule
