package com.purride.pixelui.internal

/**
 * 标记仅供 Pixel SDK 内部实现使用的非稳定二进制契约。
 *
 * 部分声明为兼容既有 JVM 描述符而保留 public 可见性；它们不属于消费者稳定 API，
 * Metalava 与 API 门禁会按本标记排除。
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
public annotation class PixelArtifactInternalApi
