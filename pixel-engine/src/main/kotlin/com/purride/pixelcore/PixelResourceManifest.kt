package com.purride.pixelcore

public class PixelResourceManifestLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

public data class PixelBitmapResourceDefinition(
    val id: String,
    val path: String,
) {
    init {
        require(id.isNotBlank()) { "bitmap id must not be blank" }
        require(path.isNotBlank()) { "bitmap path must not be blank" }
    }
}

public data class PixelSpriteSheetResourceDefinition(
    val id: String,
    val path: String,
    val bitmap: String,
) {
    init {
        require(id.isNotBlank()) { "sprite sheet id must not be blank" }
        require(path.isNotBlank()) { "sprite sheet path must not be blank" }
        require(bitmap.isNotBlank()) { "sprite sheet bitmap must not be blank" }
    }
}

public data class PixelResourceManifest(
    val bitmaps: List<PixelBitmapResourceDefinition>,
    val spriteSheets: List<PixelSpriteSheetResourceDefinition>,
    val version: Int = 1,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(version == 1) { "unsupported resource manifest version $version" }
        firstDuplicate(bitmaps.map { it.id })?.let { duplicate ->
            throw IllegalArgumentException("duplicate bitmap id '$duplicate'")
        }
        firstDuplicate(spriteSheets.map { it.id })?.let { duplicate ->
            throw IllegalArgumentException("duplicate sprite sheet id '$duplicate'")
        }
        val bitmapIds = bitmaps.map { it.id }.toSet()
        spriteSheets.forEach { sheet ->
            require(sheet.bitmap in bitmapIds) {
                "sprite sheet '${sheet.id}' references missing bitmap '${sheet.bitmap}'"
            }
        }
    }

    private fun firstDuplicate(ids: List<String>): String? {
        val seen = mutableSetOf<String>()
        return ids.firstOrNull { !seen.add(it) }
    }
}

public object PixelResourceManifestJsonLoader {
    public fun parse(json: String): PixelResourceManifest {
        return try {
            val version = optionalInt(json, "version") ?: 1
            val metadata = optionalObject(json, "metadata")?.let(::parseStringMap).orEmpty()
            val bitmaps = optionalArray(json, "bitmaps")
                ?.let { source ->
                    parseObjects(source).map { item ->
                        PixelBitmapResourceDefinition(
                            id = requireString(item, "id"),
                            path = requireString(item, "path"),
                        )
                    }
                }
                .orEmpty()
            val spriteSheets = optionalArray(json, "spriteSheets")
                ?.let { source ->
                    parseObjects(source).map { item ->
                        PixelSpriteSheetResourceDefinition(
                            id = requireString(item, "id"),
                            path = requireString(item, "path"),
                            bitmap = requireString(item, "bitmap"),
                        )
                    }
                }
                .orEmpty()

            PixelResourceManifest(
                bitmaps = bitmaps,
                spriteSheets = spriteSheets,
                version = version,
                metadata = metadata,
            )
        } catch (error: PixelResourceManifestLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PixelResourceManifestLoadException("Failed to parse resource manifest JSON: ${error.message}", error)
        }
    }

    private fun parseObjects(arraySource: String): List<String> {
        return Regex("\\{([^{}]+)\\}")
            .findAll(arraySource)
            .map { it.value }
            .toList()
    }

    private fun requireString(json: String, name: String): String {
        val match = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(json)
            ?: throw PixelResourceManifestLoadException("Missing string field '$name'")
        val value = match.groupValues[1]
        if (value.isBlank()) throw PixelResourceManifestLoadException("Field '$name' must not be blank")
        return value
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
        if (depth != 0) throw PixelResourceManifestLoadException("Unclosed object field '$name'")
        return json.substring(key.range.last + 1, index - 1)
    }

    private fun optionalArray(json: String, name: String): String? {
        val key = Regex("\"$name\"\\s*:\\s*\\[").find(json) ?: return null
        var depth = 1
        var index = key.range.last + 1
        while (index < json.length && depth > 0) {
            when (json[index]) {
                '[' -> depth += 1
                ']' -> depth -= 1
            }
            index += 1
        }
        if (depth != 0) throw PixelResourceManifestLoadException("Unclosed array field '$name'")
        return json.substring(key.range.last + 1, index - 1)
    }

    private fun parseStringMap(json: String): Map<String, String> {
        return Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
            .findAll(json)
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }
}
