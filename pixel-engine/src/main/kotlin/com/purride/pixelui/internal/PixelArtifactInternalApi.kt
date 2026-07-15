package com.purride.pixelui.internal

/**
 * 标记仅供 Pixel SDK 兄弟 artifact 互操作的内部二进制契约。
 *
 * 这些声明必须具备 JVM public 可见性，才能让拆分后的 runtime、widgets、navigation
 * 与 Android 适配层分别编译；它们不属于消费者稳定 API，Metalava 聚合门禁会按本标记排除。
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
