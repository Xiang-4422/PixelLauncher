package com.purride.pixelui.advanced

/**
 * 定义 `PixelExperimentalApi` 在 `PixelExperimentalApi` 中承担的数据与行为边界。
 *
 * Marks an advanced pixel-engine API whose contract may still evolve before it is promoted to stable.
 *
 * Experimental declarations remain source-visible, but callers must opt in explicitly so upgrades do
 * not silently change child-management, hit-testing, or other low-level behavior.
 */
@RequiresOptIn(
    message = "This pixel-engine advanced API is experimental and may change before stabilization.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class PixelExperimentalApi
