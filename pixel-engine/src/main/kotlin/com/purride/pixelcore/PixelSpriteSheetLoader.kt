package com.purride.pixelcore

public class PixelSpriteSheetLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

public data class PixelSpriteSheetDefinition(
    val bitmap: String,
    val frames: List<PixelBitmapRegion>,
) {
    init {
        require(bitmap.isNotBlank()) { "bitmap must not be blank" }
        require(frames.isNotEmpty()) { "frames must not be empty" }
    }
}

public object PixelSpriteSheetJsonLoader {
    public fun parseDefinition(json: String): PixelSpriteSheetDefinition {
        return try {
            val bitmap = requireString(json, "bitmap")
            val framesSource = requireArray(json, "frames")
            val frames = Regex("\\{([^{}]+)\\}")
                .findAll(framesSource)
                .map { match ->
                    val frameJson = match.value
                    PixelBitmapRegion(
                        left = requireInt(frameJson, "left"),
                        top = requireInt(frameJson, "top"),
                        width = requireInt(frameJson, "width"),
                        height = requireInt(frameJson, "height"),
                    )
                }
                .toList()
            PixelSpriteSheetDefinition(bitmap = bitmap, frames = frames)
        } catch (error: PixelSpriteSheetLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PixelSpriteSheetLoadException("Failed to parse sprite sheet JSON: ${error.message}", error)
        }
    }

    public fun load(json: String, bitmap: PixelBitmap): PixelSpriteSheet {
        val definition = parseDefinition(json)
        return try {
            PixelSpriteSheet(bitmap = bitmap, frames = definition.frames)
        } catch (error: Throwable) {
            throw PixelSpriteSheetLoadException("Failed to create sprite sheet for '${definition.bitmap}': ${error.message}", error)
        }
    }

    private fun requireString(json: String, name: String): String {
        val match = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(json)
            ?: throw PixelSpriteSheetLoadException("Missing string field '$name'")
        return match.groupValues[1]
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
