package com.purride.pixelcore

/** sprite sheet JSON 解析或 atlas 构建失败。 */
public class PixelSpriteSheetLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** 简单 sprite sheet 定义，只包含 bitmap id 和 frame 矩形。 */
public data class PixelSpriteSheetDefinition(
    /** 被引用的 bitmap 资源 id。 */
    val bitmap: String,
    /** 按播放顺序排列的非空帧区域。 */
    val frames: List<PixelBitmapRegion>,
    /** sheet 协议版本。 */
    val version: Int = 1,
    /** 有界字符串 metadata。 */
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(version in 1..2) { "unsupported sprite sheet version $version" }
        require(bitmap.isNotBlank()) { "bitmap must not be blank" }
        require(bitmap.length <= 256) { "bitmap id exceeds 256 chars" }
        require(frames.isNotEmpty()) { "frames must not be empty" }
        require(frames.size <= PixelResourceSafetyLimits.MaxEntries) {
            "frame count ${frames.size} exceeds ${PixelResourceSafetyLimits.MaxEntries}"
        }
        require(metadata.size <= PixelResourceSafetyLimits.MaxMetadataEntries) {
            "metadata count ${metadata.size} exceeds ${PixelResourceSafetyLimits.MaxMetadataEntries}"
        }
    }
}

/** 带裁剪和 pivot 信息的单帧 sprite 定义。 */
public data class PixelSpriteFrameDefinition(
    /** 当前帧在 bitmap 中的区域。 */
    val region: PixelBitmapRegion,
    /** 裁剪前源帧宽度。 */
    val sourceWidth: Int = region.width,
    /** 裁剪前源帧高度。 */
    val sourceHeight: Int = region.height,
    /** 当前区域相对源帧左边缘的裁剪量。 */
    val trimLeft: Int = 0,
    /** 当前区域相对源帧上边缘的裁剪量。 */
    val trimTop: Int = 0,
    /** 源帧坐标中的水平 pivot。 */
    val pivotX: Int = 0,
    /** 源帧坐标中的垂直 pivot。 */
    val pivotY: Int = 0,
) {
    init {
        require(sourceWidth in 1..PixelResourceSafetyLimits.MaxDimension) {
            "sourceWidth must be within 1..${PixelResourceSafetyLimits.MaxDimension}, got $sourceWidth"
        }
        require(sourceHeight in 1..PixelResourceSafetyLimits.MaxDimension) {
            "sourceHeight must be within 1..${PixelResourceSafetyLimits.MaxDimension}, got $sourceHeight"
        }
        require(trimLeft >= 0) { "trimLeft must be >= 0, got $trimLeft" }
        require(trimTop >= 0) { "trimTop must be >= 0, got $trimTop" }
        require(trimLeft.toLong() + region.width.toLong() <= sourceWidth.toLong()) {
            "trimmed frame exceeds sourceWidth"
        }
        require(trimTop.toLong() + region.height.toLong() <= sourceHeight.toLong()) {
            "trimmed frame exceeds sourceHeight"
        }
        require(pivotX in 0..sourceWidth) { "pivotX must be within source width" }
        require(pivotY in 0..sourceHeight) { "pivotY must be within source height" }
    }
}

/** 带源图尺寸、裁剪和 scale 信息的 atlas 定义。 */
public data class PixelSpriteAtlasDefinition(
    /** 被引用的 bitmap 资源 id。 */
    val bitmap: String,
    /** 按播放顺序排列的帧元数据。 */
    val frames: List<PixelSpriteFrameDefinition>,
    /** 逻辑尺寸到 bitmap 像素的正整数比例。 */
    val scale: Int = 1,
    /** atlas 协议版本。 */
    val version: Int = 2,
    /** 有界字符串 metadata。 */
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(bitmap.isNotBlank()) { "bitmap must not be blank" }
        require(bitmap.length <= 256) { "bitmap id exceeds 256 chars" }
        require(frames.isNotEmpty()) { "frames must not be empty" }
        require(frames.size <= PixelResourceSafetyLimits.MaxEntries) {
            "frame count ${frames.size} exceeds ${PixelResourceSafetyLimits.MaxEntries}"
        }
        require(scale in 1..PixelResourceSafetyLimits.MaxDimension) {
            "scale must be within 1..${PixelResourceSafetyLimits.MaxDimension}, got $scale"
        }
        require(version in 1..2) { "unsupported sprite sheet version $version" }
        require(metadata.size <= PixelResourceSafetyLimits.MaxMetadataEntries) {
            "metadata count ${metadata.size} exceeds ${PixelResourceSafetyLimits.MaxMetadataEntries}"
        }
    }
}

/** 已加载 bitmap 和每帧元数据的 sprite atlas。 */
public data class PixelSpriteAtlas(
    /** 可直接绘制的 sprite sheet。 */
    val sheet: PixelSpriteSheet,
    /** 与 [sheet] 帧顺序一一对应的裁剪信息。 */
    val frames: List<PixelSpriteFrameDefinition>,
    /** 逻辑尺寸到 bitmap 像素的比例。 */
    val scale: Int,
) {
    init {
        require(frames.size == sheet.frames.size) { "atlas frame metadata must match sheet frames" }
        require(scale in 1..PixelResourceSafetyLimits.MaxDimension) {
            "scale must be within 1..${PixelResourceSafetyLimits.MaxDimension}, got $scale"
        }
    }
}

/** 严格有界 JSON 解析器，用于读取 sprite sheet 和 atlas 定义。 */
public object PixelSpriteSheetJsonLoader {
    /** 解析向后兼容的简单 sheet 定义。 */
    public fun parseDefinition(json: String): PixelSpriteSheetDefinition =
        parseDefinition(json, expectedSha256 = null)

    /** 校验 JSON SHA-256 后解析简单 sheet 定义。 */
    public fun parseDefinition(json: String, expectedSha256: String?): PixelSpriteSheetDefinition {
        /** 共享 atlas 解析结果。 */
        val atlas = parseAtlasDefinition(json, expectedSha256)
        return PixelSpriteSheetDefinition(
            bitmap = atlas.bitmap,
            frames = atlas.frames.map { frame -> frame.region },
            version = atlas.version,
            metadata = atlas.metadata.toMap(),
        )
    }

    /** 解析包含 frame metadata 的 atlas 定义。 */
    public fun parseAtlasDefinition(json: String): PixelSpriteAtlasDefinition =
        parseAtlasDefinition(json, expectedSha256 = null)

    /** 校验 JSON SHA-256 后解析包含 frame metadata 的 atlas 定义。 */
    public fun parseAtlasDefinition(json: String, expectedSha256: String?): PixelSpriteAtlasDefinition {
        return wrapSheetError {
            /** atlas JSON 的原始 UTF-8 字节。 */
            val bytes = json.toByteArray(Charsets.UTF_8)
            bytes.requireSha256(expectedSha256, "sprite sheet")
            /** 经过结构、深度和重复 key 校验的顶层对象。 */
            val root = PixelBoundedJson.parseObject(
                source = json,
                limits = PixelJsonLimits(maxInputChars = PixelResourceSafetyLimits.MaxJsonChars),
            )
            /** 被引用的 bitmap id。 */
            val bitmap = root.requireString("bitmap")
            /** 未声明时保持第一版兼容的协议版本。 */
            val version = root.optionalInt("version") ?: 1
            /** 未声明时使用一个 bitmap 像素对应一个逻辑像素。 */
            val scale = root.optionalInt("scale") ?: 1
            /** 可选字符串 metadata。 */
            val metadata = root.optionalObject("metadata")
                ?.requireStringMap("metadata")
                .orEmpty()
            require(metadata.size <= PixelResourceSafetyLimits.MaxMetadataEntries) {
                "metadata count ${metadata.size} exceeds ${PixelResourceSafetyLimits.MaxMetadataEntries}"
            }
            /** 严格对象帧列表。 */
            val frameObjects = root.requireArray("frames").requireObjects("frames")
            require(frameObjects.size <= PixelResourceSafetyLimits.MaxEntries) {
                "frame count ${frameObjects.size} exceeds ${PixelResourceSafetyLimits.MaxEntries}"
            }
            /** 完成范围与溢出校验的帧定义。 */
            val frames = frameObjects.map { frame ->
                /** 当前帧 bitmap 区域。 */
                val region = PixelBitmapRegion(
                    left = frame.requireInt("left"),
                    top = frame.requireInt("top"),
                    width = frame.requireInt("width"),
                    height = frame.requireInt("height"),
                )
                PixelSpriteFrameDefinition(
                    region = region,
                    sourceWidth = frame.optionalInt("sourceWidth") ?: region.width,
                    sourceHeight = frame.optionalInt("sourceHeight") ?: region.height,
                    trimLeft = frame.optionalInt("trimLeft") ?: 0,
                    trimTop = frame.optionalInt("trimTop") ?: 0,
                    pivotX = frame.optionalInt("pivotX") ?: 0,
                    pivotY = frame.optionalInt("pivotY") ?: 0,
                )
            }
            PixelSpriteAtlasDefinition(
                bitmap = bitmap,
                frames = frames,
                scale = scale,
                version = version,
                metadata = metadata.toMap(),
            )
        }
    }

    /** 用 JSON 定义和已加载 bitmap 构建 [PixelSpriteSheet]。 */
    public fun load(json: String, bitmap: PixelBitmap): PixelSpriteSheet =
        load(json, bitmap, expectedSha256 = null)

    /** 校验 JSON SHA-256 后构建 [PixelSpriteSheet]。 */
    public fun load(
        json: String,
        bitmap: PixelBitmap,
        expectedSha256: String?,
    ): PixelSpriteSheet {
        /** 已通过结构和数量校验的 sheet 定义。 */
        val definition = parseDefinition(json, expectedSha256)
        return try {
            PixelSpriteSheet(bitmap = bitmap, frames = definition.frames.toList())
        } catch (error: Throwable) {
            throw PixelSpriteSheetLoadException(
                "Failed to create sprite sheet for '${definition.bitmap}': ${error.message}",
                error,
            )
        }
    }

    /** 用 JSON 定义和已加载 bitmap 构建 [PixelSpriteAtlas]。 */
    public fun loadAtlas(json: String, bitmap: PixelBitmap): PixelSpriteAtlas =
        loadAtlas(json, bitmap, expectedSha256 = null)

    /** 校验 JSON SHA-256 后构建 [PixelSpriteAtlas]。 */
    public fun loadAtlas(
        json: String,
        bitmap: PixelBitmap,
        expectedSha256: String?,
    ): PixelSpriteAtlas {
        /** 已通过结构和数量校验的 atlas 定义。 */
        val definition = parseAtlasDefinition(json, expectedSha256)
        return try {
            /** 共享 bitmap 的可绘制 sheet。 */
            val sheet = PixelSpriteSheet(
                bitmap = bitmap,
                frames = definition.frames.map { frame -> frame.region },
            )
            PixelSpriteAtlas(
                sheet = sheet,
                frames = definition.frames.toList(),
                scale = definition.scale,
            )
        } catch (error: Throwable) {
            throw PixelSpriteSheetLoadException(
                "Failed to create sprite atlas for '${definition.bitmap}': ${error.message}",
                error,
            )
        }
    }

    /** 把实现异常统一包装为稳定的公开解析异常。 */
    private inline fun <T> wrapSheetError(block: () -> T): T {
        return try {
            block()
        } catch (error: PixelSpriteSheetLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PixelSpriteSheetLoadException(
                "Failed to parse sprite sheet JSON: ${error.message}",
                error,
            )
        }
    }
}
