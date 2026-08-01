package com.purride.pixelui

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** 系统图标的像素画布规格；所有规格保持奇数边长，便于单像素笔画精确居中。 */
public enum class PixelSystemIconSize(
    /** 图标位图的正方形边长，单位为逻辑像素。 */
    public val pixels: Int,
) {
    /** 紧凑行内图标。 */
    SMALL(7),

    /** 常规控件图标。 */
    MEDIUM(9),

    /** 较大设置文字旁的图标。 */
    LARGE(11),

    /** 独立操作按钮或大字号界面的图标。 */
    EXTRA_LARGE(15),
}

/** Launcher 与 Showcase 共用的稳定系统图标语义；无障碍名称由具体控件按上下文提供。 */
public enum class PixelSystemIcon {
    /** 方形像素样式。 */
    SQUARE,

    /** 圆形像素样式。 */
    CIRCLE,

    /** 菱形像素样式。 */
    DIAMOND,

    /** 固定日间主题。 */
    DAY,

    /** 跟随系统主题自动切换。 */
    AUTO,

    /** 固定夜间主题。 */
    NIGHT,

    /** 内容左对齐。 */
    ALIGN_LEFT,

    /** 内容居中对齐。 */
    ALIGN_CENTER,

    /** 内容右对齐。 */
    ALIGN_RIGHT,

    /** 返回上一层。 */
    BACK,

    /** 前进到下一项或进入下一层。 */
    FORWARD,

    /** 向上移动或选择。 */
    ARROW_UP,

    /** 向下移动或选择。 */
    ARROW_DOWN,

    /** 语音信箱。 */
    VOICEMAIL,

    /** 关闭当前内容。 */
    CLOSE,

    /** 搜索内容。 */
    SEARCH,

    /** 编辑内容。 */
    EDIT,

    /** 删除内容。 */
    DELETE,

    /** 增加数值或项目。 */
    ADD,

    /** 减少数值或项目。 */
    REMOVE,
}

/**
 * 提供与字体家族解耦的确定性像素图标。
 *
 * 每个规格都根据自身画布重新栅格化，不对小位图做插值缩放；返回结果可直接绘制，
 * 也可作为 [IconButton] 等控件的透明度遮罩使用。
 */
public object PixelSystemIcons {
    /** 已生成图标按语义、规格和颜色缓存，避免页面重建时重复分配位图。 */
    private val cache: ConcurrentHashMap<PixelSystemIconCacheKey, PixelIconData> = ConcurrentHashMap()

    /** 返回使用不透明白色绘制的标准遮罩图标。 */
    public fun mask(icon: PixelSystemIcon, size: PixelSystemIconSize): PixelIconData {
        return tinted(icon = icon, size = size, color = PixelColor.White)
    }

    /** 返回使用指定颜色栅格化的图标，透明区域保持完全透明。 */
    public fun tinted(
        icon: PixelSystemIcon,
        size: PixelSystemIconSize,
        color: PixelColor,
    ): PixelIconData {
        /** 缓存键包含颜色，确保主题切换后不会复用旧色位图。 */
        val key = PixelSystemIconCacheKey(icon = icon, size = size, colorArgb = color.argb)
        return cache.getOrPut(key) {
            PixelIconData(rasterize(icon = icon, canvasSize = size.pixels, color = color))
        }
    }

    /** 按图标语义分派到对应的像素几何绘制规则。 */
    private fun rasterize(icon: PixelSystemIcon, canvasSize: Int, color: PixelColor): PixelBitmap {
        /** 当前图标独占的透明画布。 */
        val canvas = PixelIconCanvas(size = canvasSize)
        when (icon) {
            PixelSystemIcon.SQUARE -> canvas.drawSquare()
            PixelSystemIcon.CIRCLE -> canvas.drawCircle()
            PixelSystemIcon.DIAMOND -> canvas.drawDiamond()
            PixelSystemIcon.DAY -> canvas.drawDay()
            PixelSystemIcon.AUTO -> canvas.drawAuto()
            PixelSystemIcon.NIGHT -> canvas.drawNight()
            PixelSystemIcon.ALIGN_LEFT -> canvas.drawAlignment(PixelIconAlignment.LEFT)
            PixelSystemIcon.ALIGN_CENTER -> canvas.drawAlignment(PixelIconAlignment.CENTER)
            PixelSystemIcon.ALIGN_RIGHT -> canvas.drawAlignment(PixelIconAlignment.RIGHT)
            PixelSystemIcon.BACK -> canvas.drawBack()
            PixelSystemIcon.FORWARD -> canvas.drawForward()
            PixelSystemIcon.ARROW_UP -> canvas.drawArrowUp()
            PixelSystemIcon.ARROW_DOWN -> canvas.drawArrowDown()
            PixelSystemIcon.VOICEMAIL -> canvas.drawVoicemail()
            PixelSystemIcon.CLOSE -> canvas.drawClose()
            PixelSystemIcon.SEARCH -> canvas.drawSearch()
            PixelSystemIcon.EDIT -> canvas.drawEdit()
            PixelSystemIcon.DELETE -> canvas.drawDelete()
            PixelSystemIcon.ADD -> canvas.drawAdd()
            PixelSystemIcon.REMOVE -> canvas.drawRemove()
        }
        return canvas.toBitmap(color)
    }
}

/** 单个位图缓存项的完整身份。 */
private data class PixelSystemIconCacheKey(
    /** 图标语义。 */
    val icon: PixelSystemIcon,
    /** 图标画布规格。 */
    val size: PixelSystemIconSize,
    /** 已解析主题颜色的 ARGB 值。 */
    val colorArgb: Int,
)

/** 对齐图标中短横线相对长横线的锚定方向。 */
private enum class PixelIconAlignment {
    /** 所有横线左边缘对齐。 */
    LEFT,

    /** 所有横线围绕中心线对齐。 */
    CENTER,

    /** 所有横线右边缘对齐。 */
    RIGHT,
}

/** 在奇数正方形网格上绘制一像素笔画的内部遮罩画布。 */
private class PixelIconCanvas(
    /** 正方形画布边长。 */
    private val size: Int,
) {
    /** 每个逻辑像素是否属于图标墨迹。 */
    private val ink: BooleanArray = BooleanArray(size * size)

    /** 方形样式使用完整闭合的一像素描边。 */
    fun drawSquare() {
        /** 随图标尺寸增加的光学留白。 */
        val inset = shapeInset()
        horizontal(y = inset, startX = inset, endX = size - inset - 1)
        horizontal(y = size - inset - 1, startX = inset, endX = size - inset - 1)
        vertical(x = inset, startY = inset, endY = size - inset - 1)
        vertical(x = size - inset - 1, startY = inset, endY = size - inset - 1)
    }

    /** 圆形样式按每档半径重新采样一像素圆环。 */
    fun drawCircle() {
        /** 圆心固定在奇数画布的中心像素。 */
        val center = size / 2
        /** 外半径保留与画布规格匹配的边缘留白。 */
        val outerRadius = center - shapeInset() + 0.5
        /** 内半径与外半径相差一个像素笔画。 */
        val innerRadius = (outerRadius - 0.75).coerceAtLeast(0.0)
        fillDistanceRing(
            centerX = center,
            centerY = center,
            outerRadius = outerRadius,
            innerRadius = innerRadius,
        )
    }

    /** 菱形样式使用曼哈顿距离构造对称的一像素轮廓。 */
    fun drawDiamond() {
        /** 菱形中心点。 */
        val center = size / 2
        /** 顶点到中心的逻辑像素距离。 */
        val radius = center - shapeInset()
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (kotlin.math.abs(x - center) + kotlin.math.abs(y - center) == radius) set(x, y)
            }
        }
    }

    /** 日间图标使用实心太阳与八个相互分离的方向光芒。 */
    fun drawDay() {
        /** 太阳和光芒共享的中心坐标。 */
        val center = size / 2
        /** 实心太阳半径随画布规格阶梯增长。 */
        val bodyRadius = (size / 5).coerceAtLeast(1)
        fillDistanceDisk(centerX = center, centerY = center, radius = bodyRadius + 0.25)
        /** 正交光芒到中心的距离，最小规格落在画布最外沿。 */
        val axisOffset = center - shapeInset() + 1
        set(center, center - axisOffset)
        set(center, center + axisOffset)
        set(center - axisOffset, center)
        set(center + axisOffset, center)
        /** 对角光芒略靠近中心，保持与圆形主体之间至少一个透明像素。 */
        val diagonalOffset = (axisOffset - 1).coerceAtLeast(bodyRadius + 1)
        set(center - diagonalOffset, center - diagonalOffset)
        set(center + diagonalOffset, center - diagonalOffset)
        set(center - diagonalOffset, center + diagonalOffset)
        set(center + diagonalOffset, center + diagonalOffset)
    }

    /** 自动模式使用首尾相接的双向循环箭头表达跟随系统变化。 */
    fun drawAuto() {
        /** 两条循环箭头围绕的中心坐标。 */
        val center = size / 2
        /** 上下箭身与中心之间的距离。 */
        val verticalOffset = (size / 4).coerceAtLeast(1)
        /** 箭身左右留白。 */
        val margin = shapeInset()
        /** 上方箭头从左向右。 */
        horizontal(y = center - verticalOffset, startX = margin, endX = size - margin - 1)
        /** 下方箭头从右向左。 */
        horizontal(y = center + verticalOffset, startX = margin, endX = size - margin - 1)
        /** 箭头翼长在大规格下增加，仍保持单像素笔画。 */
        val wingLength = if (size >= PixelSystemIconSize.LARGE.pixels) 2 else 1
        repeat(wingLength) { offset ->
            set(size - margin - offset - 2, center - verticalOffset - offset - 1)
            set(size - margin - offset - 2, center - verticalOffset + offset + 1)
            set(margin + offset + 1, center + verticalOffset - offset - 1)
            set(margin + offset + 1, center + verticalOffset + offset + 1)
        }
    }

    /** 夜间图标使用逐规格审校的满月与陨石坑，避免小画布上的月牙退化为括号。 */
    fun drawNight() {
        /** 当前画布对应的人工审校轮廓；透明负形表达不同大小的陨石坑。 */
        val rows = when (size) {
            PixelSystemIconSize.SMALL.pixels -> listOf(
                "..###..",
                ".#####.",
                "####.##",
                "#######",
                "##.####",
                ".#####.",
                "..###..",
            )
            PixelSystemIconSize.MEDIUM.pixels -> listOf(
                "...###...",
                ".#######.",
                "#####..##",
                "#####.###",
                "##.######",
                "#########",
                "#####.###",
                ".#######.",
                "...###...",
            )
            PixelSystemIconSize.LARGE.pixels -> listOf(
                "....###....",
                "..#######..",
                ".######..#.",
                "#######.###",
                "###########",
                "##..#######",
                "###.#######",
                "###########",
                ".######..#.",
                "..#######..",
                "....###....",
            )
            PixelSystemIconSize.EXTRA_LARGE.pixels -> listOf(
                "......###......",
                "....#######....",
                "..#######..##..",
                ".########..###.",
                "###############",
                "###############",
                "###..##########",
                "###..##########",
                "###############",
                "#########.#####",
                "#########..####",
                "###############",
                "..###########..",
                "....#######....",
                "......###......",
            )
            else -> error("Unsupported system icon size: $size")
        }
        drawTemplate(rows)
    }

    /** 用交替长短横线表达左、中、右三种文本对齐方式。 */
    fun drawAlignment(alignment: PixelIconAlignment) {
        /** 对齐横线的左右安全留白。 */
        val margin = if (size >= 11) 2 else 1
        /** 最长横线宽度。 */
        val fullWidth = size - margin * 2
        /** 短横线保持奇数宽度，保证居中对齐时不落在半像素。 */
        val shortWidth = ((fullWidth * 2 / 3).coerceAtLeast(3)) or 1
        /** 小规格使用三行，其他规格使用四行，避免相邻横线粘连。 */
        val lineCount = if (size == PixelSystemIconSize.SMALL.pixels) 3 else 4
        /** 可用于分布横线的纵向范围。 */
        val verticalSpan = size - margin * 2 - 1
        repeat(lineCount) { index ->
            /** 当前横线在纵向范围内的等距位置。 */
            val y = margin + (verticalSpan * index.toFloat() / (lineCount - 1)).roundToInt()
            /** 奇数行使用短横线，让方向锚点具备辨识度。 */
            val width = if (index % 2 == 0) fullWidth else shortWidth
            /** 根据语义计算短横线的左边缘。 */
            val startX = when (alignment) {
                PixelIconAlignment.LEFT -> margin
                PixelIconAlignment.CENTER -> (size - width) / 2
                PixelIconAlignment.RIGHT -> size - margin - width
            }
            horizontal(y = y, startX = startX, endX = startX + width - 1)
        }
    }

    /** 返回图标由左箭头和贯穿中心的水平笔画组成。 */
    fun drawBack() {
        /** 箭头中心纵坐标。 */
        val center = size / 2
        /** 箭头与画布边缘之间的留白。 */
        val margin = shapeInset()
        /** 箭头上下两翼的长度。 */
        val headRadius = (size - margin * 2) / 2
        horizontal(y = center, startX = margin, endX = size - margin - 1)
        repeat(headRadius + 1) { offset ->
            set(margin + offset, center - offset)
            set(margin + offset, center + offset)
        }
    }

    /** 前进图标镜像返回图标，保留同等箭头尺寸与水平笔画。 */
    fun drawForward() {
        /** 箭头中心纵坐标。 */
        val center = size / 2
        /** 箭头与画布边缘之间的留白。 */
        val margin = shapeInset()
        /** 箭头上下两翼的长度。 */
        val headRadius = (size - margin * 2) / 2
        horizontal(y = center, startX = margin, endX = size - margin - 1)
        repeat(headRadius + 1) { offset ->
            set(size - margin - offset - 1, center - offset)
            set(size - margin - offset - 1, center + offset)
        }
    }

    /** 向上箭头使用竖直箭身和关于中心线对称的箭头翼。 */
    fun drawArrowUp() {
        /** 箭头中心横坐标。 */
        val center = size / 2
        /** 箭头与画布边缘之间的留白。 */
        val margin = shapeInset()
        /** 箭头左右两翼的长度。 */
        val headRadius = (size - margin * 2) / 2
        vertical(x = center, startY = margin, endY = size - margin - 1)
        repeat(headRadius + 1) { offset ->
            set(center - offset, margin + offset)
            set(center + offset, margin + offset)
        }
    }

    /** 向下箭头镜像向上箭头，保持四方向按钮相同视觉重量。 */
    fun drawArrowDown() {
        /** 箭头中心横坐标。 */
        val center = size / 2
        /** 箭头与画布边缘之间的留白。 */
        val margin = shapeInset()
        /** 箭头左右两翼的长度。 */
        val headRadius = (size - margin * 2) / 2
        vertical(x = center, startY = margin, endY = size - margin - 1)
        repeat(headRadius + 1) { offset ->
            set(center - offset, size - margin - offset - 1)
            set(center + offset, size - margin - offset - 1)
        }
    }

    /** 语音信箱使用两个磁带卷盘和底部连接线形成稳定的国际通用符号。 */
    fun drawVoicemail() {
        /** 两个卷盘共享的纵向中心。 */
        val centerY = size / 2
        /** 卷盘半径随规格增长，最小规格仍保留一像素孔洞。 */
        val radius = (size / 5).coerceAtLeast(1)
        /** 左侧卷盘中心。 */
        val leftCenterX = size / 2 - radius - 1
        /** 右侧卷盘中心。 */
        val rightCenterX = size / 2 + radius + 1
        fillDistanceRing(
            centerX = leftCenterX,
            centerY = centerY,
            outerRadius = radius + 0.35,
            innerRadius = (radius - 0.4).coerceAtLeast(0.0),
        )
        fillDistanceRing(
            centerX = rightCenterX,
            centerY = centerY,
            outerRadius = radius + 0.35,
            innerRadius = (radius - 0.4).coerceAtLeast(0.0),
        )
        horizontal(
            y = centerY + radius,
            startX = leftCenterX,
            endX = rightCenterX,
        )
    }

    /** 关闭图标使用两条完整对角线，所有规格都保持一像素笔画。 */
    fun drawClose() {
        /** 对角线与画布边缘之间的留白。 */
        val inset = shapeInset()
        for (coordinate in inset until size - inset) {
            set(coordinate, coordinate)
            set(size - coordinate - 1, coordinate)
        }
    }

    /** 搜索图标由独立圆环和向右下延伸的手柄组成。 */
    fun drawSearch() {
        /** 放大镜圆环半径，随画布规格阶梯增长。 */
        val radius = ((size - 1) / 4).coerceAtLeast(1)
        /** 圆心向左上偏移，为手柄预留空间。 */
        val center = radius + 1
        fillDistanceRing(
            centerX = center,
            centerY = center,
            outerRadius = radius + 0.35,
            innerRadius = (radius - 0.4).coerceAtLeast(0.0),
        )
        /** 手柄从圆环右下角延伸到安全边缘。 */
        val handleStart = center + radius
        for (coordinate in handleStart until size - 1) set(coordinate, coordinate)
    }

    /** 编辑图标使用右上到左下的铅笔主体，并在尾部增加握持宽度。 */
    fun drawEdit() {
        /** 铅笔主体与画布边缘之间的留白。 */
        val inset = shapeInset()
        /** 沿右上至左下方向绘制铅笔中心线。 */
        for (offset in 0 until size - inset * 2) {
            val x = size - inset - offset - 1
            val y = inset + offset
            set(x, y)
            if (size >= PixelSystemIconSize.MEDIUM.pixels) set(x - 1, y)
        }
        /** 左下角的短线形成笔尖与编辑落点。 */
        horizontal(y = size - inset - 1, startX = inset, endX = inset + 2)
    }

    /** 删除图标绘制带顶盖和中间分隔线的像素垃圾桶。 */
    fun drawDelete() {
        /** 垃圾桶与画布边缘之间的留白。 */
        val inset = shapeInset()
        /** 桶体顶部纵坐标。 */
        val top = inset + 2
        /** 桶体底部纵坐标。 */
        val bottom = size - inset - 1
        horizontal(y = inset + 1, startX = inset, endX = size - inset - 1)
        horizontal(y = inset, startX = size / 2 - 1, endX = size / 2 + 1)
        vertical(x = inset + 1, startY = top, endY = bottom)
        vertical(x = size - inset - 2, startY = top, endY = bottom)
        horizontal(y = bottom, startX = inset + 1, endX = size - inset - 2)
        if (size >= PixelSystemIconSize.MEDIUM.pixels) {
            vertical(x = size / 2, startY = top + 1, endY = bottom - 1)
        }
    }

    /** 增加图标绘制水平与垂直两条等长中心线。 */
    fun drawAdd() {
        /** 图标中心坐标。 */
        val center = size / 2
        /** 加号臂长随规格增长，同时保留边缘留白。 */
        val radius = center - shapeInset()
        horizontal(y = center, startX = center - radius, endX = center + radius)
        vertical(x = center, startY = center - radius, endY = center + radius)
    }

    /** 减少图标只绘制与增加图标等宽的水平中心线。 */
    fun drawRemove() {
        /** 图标中心坐标。 */
        val center = size / 2
        /** 减号半宽随规格增长，同时保留边缘留白。 */
        val radius = center - shapeInset()
        horizontal(y = center, startX = center - radius, endX = center + radius)
    }

    /** 将当前布尔遮罩转换为调用方指定颜色的不可变位图。 */
    fun toBitmap(color: PixelColor): PixelBitmap {
        /** 透明背景与指定前景色组成的 ARGB 像素数组。 */
        val pixels = IntArray(ink.size) { index -> if (ink[index]) color.argb else PixelColor.Transparent.argb }
        return PixelBitmap(width = size, height = size, pixels = pixels)
    }

    /** 返回形状类图标随画布增长的安全留白。 */
    private fun shapeInset(): Int = (size / 6).coerceAtLeast(1)

    /** 绘制一个由平方距离范围定义的一像素圆环。 */
    private fun fillDistanceRing(
        centerX: Int,
        centerY: Int,
        outerRadius: Double,
        innerRadius: Double,
    ) {
        /** 外圆半径平方，避免为每个像素计算平方根。 */
        val outerSquared = outerRadius * outerRadius
        /** 内圆半径平方。 */
        val innerSquared = innerRadius * innerRadius
        for (y in 0 until size) {
            for (x in 0 until size) {
                /** 当前像素中心到圆心的横向距离。 */
                val dx = (x - centerX).toDouble()
                /** 当前像素中心到圆心的纵向距离。 */
                val dy = (y - centerY).toDouble()
                /** 当前像素中心到圆心的平方距离。 */
                val distanceSquared = dx * dx + dy * dy
                if (distanceSquared <= outerSquared && distanceSquared >= innerSquared) set(x, y)
            }
        }
    }

    /** 填充一个以像素中心平方距离判定的实心圆盘。 */
    private fun fillDistanceDisk(centerX: Int, centerY: Int, radius: Double) {
        /** 圆盘半径平方。 */
        val radiusSquared = radius * radius
        for (y in 0 until size) {
            for (x in 0 until size) {
                /** 当前像素中心到圆心的横向距离。 */
                val dx = (x - centerX).toDouble()
                /** 当前像素中心到圆心的纵向距离。 */
                val dy = (y - centerY).toDouble()
                if (dx * dx + dy * dy <= radiusSquared) set(x, y)
            }
        }
    }

    /** 把使用井号标记墨迹的人工像素模板写入当前画布。 */
    private fun drawTemplate(rows: List<String>) {
        require(rows.size == size) { "Template height ${rows.size} does not match canvas size $size" }
        rows.forEachIndexed { y, row ->
            require(row.length == size) { "Template row width ${row.length} does not match canvas size $size" }
            row.forEachIndexed { x, pixel ->
                if (pixel == '#') set(x, y)
            }
        }
    }

    /** 在画布范围内设置一个墨迹像素，越界点被安全忽略。 */
    private fun set(x: Int, y: Int) {
        if (x in 0 until size && y in 0 until size) ink[y * size + x] = true
    }

    /** 绘制包含首尾端点的水平线。 */
    private fun horizontal(y: Int, startX: Int, endX: Int) {
        for (x in startX..endX) set(x, y)
    }

    /** 绘制包含首尾端点的垂直线。 */
    private fun vertical(x: Int, startY: Int, endY: Int) {
        for (y in startY..endY) set(x, y)
    }
}
