package com.purride.pixelcore

/** sprite sheet JSON 解析或 atlas 构建失败。 */
public class PixelSpriteSheetLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** 简单 sprite sheet 定义，只包含 bitmap id 和 frame 矩形。 */
public data class PixelSpriteSheetDefinition(
    val bitmap: String,
    val frames: List<PixelBitmapRegion>,
    val version: Int = 1,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(version in 1..2) { "unsupported sprite sheet version $version" }
        require(bitmap.isNotBlank()) { "bitmap must not be blank" }
        require(frames.isNotEmpty()) { "frames must not be empty" }
    }
}

/** 带裁剪和 pivot 信息的单帧 sprite 定义。 */
public data class PixelSpriteFrameDefinition(
    val region: PixelBitmapRegion,
    val sourceWidth: Int = region.width,
    val sourceHeight: Int = region.height,
    val trimLeft: Int = 0,
    val trimTop: Int = 0,
    val pivotX: Int = 0,
    val pivotY: Int = 0,
) {
    init {
        require(sourceWidth > 0) { "sourceWidth must be > 0, got $sourceWidth" }
        require(sourceHeight > 0) { "sourceHeight must be > 0, got $sourceHeight" }
        require(trimLeft >= 0) { "trimLeft must be >= 0, got $trimLeft" }
        require(trimTop >= 0) { "trimTop must be >= 0, got $trimTop" }
        require(trimLeft + region.width <= sourceWidth) { "trimmed frame exceeds sourceWidth" }
        require(trimTop + region.height <= sourceHeight) { "trimmed frame exceeds sourceHeight" }
        require(pivotX in 0..sourceWidth) { "pivotX must be within source width" }
        require(pivotY in 0..sourceHeight) { "pivotY must be within source height" }
    }
}

/** 带源图尺寸、裁剪和 scale 信息的 atlas 定义。 */
public data class PixelSpriteAtlasDefinition(
    val bitmap: String,
    val frames: List<PixelSpriteFrameDefinition>,
    val scale: Int = 1,
    val version: Int = 2,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(bitmap.isNotBlank()) { "bitmap must not be blank" }
        require(frames.isNotEmpty()) { "frames must not be empty" }
        require(scale > 0) { "scale must be > 0, got $scale" }
        require(version in 1..2) { "unsupported sprite sheet version $version" }
    }
}

/** 已加载 bitmap 和每帧元数据的 sprite atlas。 */
public data class PixelSpriteAtlas(
    val sheet: PixelSpriteSheet,
    val frames: List<PixelSpriteFrameDefinition>,
    val scale: Int,
) {
    init {
        require(frames.size == sheet.frames.size) { "atlas frame metadata must match sheet frames" }
        require(scale > 0) { "scale must be > 0, got $scale" }
    }
}

/** 轻量 JSON 解析器，用于读取 sprite sheet 和 atlas 定义。 */
public object PixelSpriteSheetJsonLoader {
    /** 解析向后兼容的简单 sheet 定义。 */
    public fun parseDefinition(json: String): PixelSpriteSheetDefinition {
        val atlas = parseAtlasDefinition(json)
        return PixelSpriteSheetDefinition(
            bitmap = atlas.bitmap,
            frames = atlas.frames.map { it.region },
            version = atlas.version,
            metadata = atlas.metadata,
        )
    }

    /** 解析包含 frame metadata 的 atlas 定义。 */
    public fun parseAtlasDefinition(json: String): PixelSpriteAtlasDefinition {
        return try {
            val bitmap = requireString(json, "bitmap")
            val version = optionalInt(json, "version") ?: 1
            val scale = optionalInt(json, "scale") ?: 1
            val metadata = optionalObject(json, "metadata")?.let(::parseStringMap).orEmpty()
            val framesSource = requireArray(json, "frames")
            val frames = parseObjects(framesSource)
                .map { frameJson ->
                    val region = PixelBitmapRegion(
                        left = requireInt(frameJson, "left"),
                        top = requireInt(frameJson, "top"),
                        width = requireInt(frameJson, "width"),
                        height = requireInt(frameJson, "height"),
                    )
                    PixelSpriteFrameDefinition(
                        region = region,
                        sourceWidth = optionalInt(frameJson, "sourceWidth") ?: region.width,
                        sourceHeight = optionalInt(frameJson, "sourceHeight") ?: region.height,
                        trimLeft = optionalInt(frameJson, "trimLeft") ?: 0,
                        trimTop = optionalInt(frameJson, "trimTop") ?: 0,
                        pivotX = optionalInt(frameJson, "pivotX") ?: 0,
                        pivotY = optionalInt(frameJson, "pivotY") ?: 0,
                    )
                }
            PixelSpriteAtlasDefinition(
                bitmap = bitmap,
                frames = frames,
                scale = scale,
                version = version,
                metadata = metadata,
            )
        } catch (error: PixelSpriteSheetLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PixelSpriteSheetLoadException("Failed to parse sprite sheet JSON: ${error.message}", error)
        }
    }

    /** 用 JSON 定义和已加载 bitmap 构建 [PixelSpriteSheet]。 */
    public fun load(json: String, bitmap: PixelBitmap): PixelSpriteSheet {
        val definition = parseDefinition(json)
        return try {
            PixelSpriteSheet(bitmap = bitmap, frames = definition.frames)
        } catch (error: Throwable) {
            throw PixelSpriteSheetLoadException("Failed to create sprite sheet for '${definition.bitmap}': ${error.message}", error)
        }
    }

    /** 用 JSON 定义和已加载 bitmap 构建 [PixelSpriteAtlas]。 */
    public fun loadAtlas(json: String, bitmap: PixelBitmap): PixelSpriteAtlas {
        val definition = parseAtlasDefinition(json)
        return try {
            val sheet = PixelSpriteSheet(
                bitmap = bitmap,
                frames = definition.frames.map { it.region },
            )
            PixelSpriteAtlas(
                sheet = sheet,
                frames = definition.frames,
                scale = definition.scale,
            )
        } catch (error: Throwable) {
            throw PixelSpriteSheetLoadException("Failed to create sprite atlas for '${definition.bitmap}': ${error.message}", error)
        }
    }

    private fun parseObjects(arraySource: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        arraySource.forEachIndexed { index, char ->
            when (char) {
                '{' -> {
                    if (depth == 0) start = index
                    depth += 1
                }
                '}' -> {
                    depth -= 1
                    if (depth < 0) throw PixelSpriteSheetLoadException("Unexpected closing frame object")
                    if (depth == 0 && start >= 0) {
                        objects += arraySource.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        if (depth != 0) throw PixelSpriteSheetLoadException("Unclosed frame object")
        return objects
    }

    private fun requireString(json: String, name: String): String {
        val match = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(json)
            ?: throw PixelSpriteSheetLoadException("Missing string field '$name'")
        return match.groupValues[1]
    }

    private fun optionalInt(json: String, name: String): Int? {
        return Regex("\"$name\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toInt()
    }

    private fun optionalObject(json: String, name: String): String? {
        val key = Regex("\"$name\"\\s*:\\s*\\{").find(json) ?: return null
        var depth = 1
        var index = key.range.last + 1
        while (index < json.length && depth > 0) {
            when (json[index]) {
                '{' -> depth += 1
                '}' -> depth -= 1
            }
            index += 1
        }
        if (depth != 0) throw PixelSpriteSheetLoadException("Unclosed object field '$name'")
        return json.substring(key.range.last + 1, index - 1)
    }

    private fun parseStringMap(json: String): Map<String, String> {
        return Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
            .findAll(json)
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }

    private fun requireArray(json: String, name: String): String {
        val key = Regex("\"$name\"\\s*:\\s*\\[").find(json)
            ?: throw PixelSpriteSheetLoadException("Missing array field '$name'")
        var depth = 1
        var index = key.range.last + 1
        while (index < json.length && depth > 0) {
            when (json[index]) {
                '[' -> depth += 1
                ']' -> depth -= 1
            }
            index += 1
        }
        if (depth != 0) throw PixelSpriteSheetLoadException("Unclosed array field '$name'")
        return json.substring(key.range.last + 1, index - 1)
    }

    private fun requireInt(json: String, name: String): Int {
        val match = Regex("\"$name\"\\s*:\\s*(-?\\d+)").find(json)
            ?: throw PixelSpriteSheetLoadException("Missing int field '$name'")
        return match.groupValues[1].toInt()
    }
}
