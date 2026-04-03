package com.purride.pixelui.internal

/**
 * viewport 子缓冲渲染后，把交互目标平移回父坐标系的 support。
 */
internal object PixelTargetTranslateSupport {
    fun translateClickTargets(
        targets: List<PixelClickTarget>,
        parentBounds: PixelRect,
        deltaX: Int,
        deltaY: Int,
        into: MutableList<PixelClickTarget>,
    ) {
        targets.forEach { target ->
            target.bounds
                .translate(
                    deltaX = parentBounds.left + deltaX,
                    deltaY = parentBounds.top + deltaY,
                )
                .intersect(parentBounds)
                ?.let { translatedBounds ->
                    into += PixelClickTarget(
                        bounds = translatedBounds,
                        onClick = target.onClick,
                    )
                }
        }
    }

    fun translatePagerTargets(
        targets: List<PixelPagerTarget>,
        parentBounds: PixelRect,
        deltaX: Int,
        deltaY: Int,
        into: MutableList<PixelPagerTarget>,
    ) {
        translateCopyTargets(
            targets = targets,
            parentBounds = parentBounds,
            deltaX = deltaX,
            deltaY = deltaY,
            into = into,
        ) { target, bounds -> target.copy(bounds = bounds) }
    }

    fun translateListTargets(
        targets: List<PixelListTarget>,
        parentBounds: PixelRect,
        deltaX: Int,
        deltaY: Int,
        into: MutableList<PixelListTarget>,
    ) {
        translateCopyTargets(
            targets = targets,
            parentBounds = parentBounds,
            deltaX = deltaX,
            deltaY = deltaY,
            into = into,
        ) { target, bounds -> target.copy(bounds = bounds) }
    }

    fun translateTextInputTargets(
        targets: List<PixelTextInputTarget>,
        parentBounds: PixelRect,
        deltaX: Int,
        deltaY: Int,
        into: MutableList<PixelTextInputTarget>,
    ) {
        translateCopyTargets(
            targets = targets,
            parentBounds = parentBounds,
            deltaX = deltaX,
            deltaY = deltaY,
            into = into,
        ) { target, bounds -> target.copy(bounds = bounds) }
    }

    private fun <T> translateCopyTargets(
        targets: List<T>,
        parentBounds: PixelRect,
        deltaX: Int,
        deltaY: Int,
        into: MutableList<T>,
        copyWithBounds: (T, PixelRect) -> T,
    ) where T : Any {
        targets.forEach { target ->
            val bounds = when (target) {
                is PixelPagerTarget -> target.bounds
                is PixelListTarget -> target.bounds
                is PixelTextInputTarget -> target.bounds
                else -> error("不支持的 target 类型：${target::class.simpleName}")
            }
            bounds.translate(
                deltaX = parentBounds.left + deltaX,
                deltaY = parentBounds.top + deltaY,
            ).intersect(parentBounds)?.let { translatedBounds ->
                into += copyWithBounds(target, translatedBounds)
            }
        }
    }
}
