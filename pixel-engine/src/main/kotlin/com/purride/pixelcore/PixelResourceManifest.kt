package com.purride.pixelcore

/** 资源 manifest 或 catalog JSON 解析失败。 */
public class PixelResourceManifestLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** manifest 中的一张 bitmap 资源定义。 */
public data class PixelBitmapResourceDefinition(
    val id: String,
    val path: String,
) {
    init {
        require(id.isNotBlank()) { "bitmap id must not be blank" }
        require(path.isNotBlank()) { "bitmap path must not be blank" }
    }
}

/** manifest 中的一张 sprite sheet 定义，引用已声明的 bitmap。 */
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

/** catalog 中的命名颜色资源。 */
public data class PixelColorResourceDefinition(
    val id: String,
    val color: PixelColor,
) {
    init {
        require(id.isNotBlank()) { "color id must not be blank" }
    }
}

/** catalog 中的字形包资源，包含 manifest 和二进制 glyph 路径。 */
public data class PixelFontResourceDefinition(
    val id: String,
    val manifestPath: String,
    val binaryPath: String,
) {
    init {
        require(id.isNotBlank()) { "font id must not be blank" }
        require(manifestPath.isNotBlank()) { "font manifestPath must not be blank" }
        require(binaryPath.isNotBlank()) { "font binaryPath must not be blank" }
    }
}

/** bitmap 与 sprite sheet 的基础资源清单。 */
public data class PixelResourceManifest(
    val bitmaps: List<PixelBitmapResourceDefinition>,
    val spriteSheets: List<PixelSpriteSheetResourceDefinition>,
    val version: Int = 1,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(version in 1..2) { "unsupported resource manifest version $version" }
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

/** 完整资源目录，在基础 manifest 之外增加颜色和字体资源。 */
public data class PixelResourceCatalog(
    val resources: PixelResourceManifest,
    val colors: List<PixelColorResourceDefinition>,
    val fonts: List<PixelFontResourceDefinition>,
) {
    init {
        require(resources.version == 2 || (colors.isEmpty() && fonts.isEmpty())) {
            "resource manifest version 2 is required for colors or fonts"
        }
        val ids = buildList {
            addAll(resources.bitmaps.map { it.id })
            addAll(resources.spriteSheets.map { it.id })
            addAll(colors.map { it.id })
            addAll(fonts.map { it.id })
        }
        val duplicate = ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
        require(duplicate == null) { "duplicate resource id '$duplicate'" }
    }
}

/** 轻量 JSON 解析器，用于读取 pixel-engine 资源 manifest。 */
public object PixelResourceManifestJsonLoader {
    /** 解析基础 bitmap/sprite manifest。 */
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

    /** 解析完整 catalog，包含 colors 和 fonts。 */
    public fun parseCatalog(json: String): PixelResourceCatalog {
        return try {
            val resources = parse(json)
            val colors = optionalArray(json, "colors")
                ?.let { source ->
                    parseObjects(source).map { item ->
                        PixelColorResourceDefinition(
                            id = requireString(item, "id"),
                            color = parseColor(requireString(item, "value")),
                        )
                    }
                }
                .orEmpty()
            val fonts = optionalArray(json, "fonts")
                ?.let { source ->
                    parseObjects(source).map { item ->
                        PixelFontResourceDefinition(
                            id = requireString(item, "id"),
                            manifestPath = requireString(item, "manifest"),
                            binaryPath = requireString(item, "binary"),
                        )
                    }
                }
                .orEmpty()
            PixelResourceCatalog(resources = resources, colors = colors, fonts = fonts)
        } catch (error: PixelResourceManifestLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PixelResourceManifestLoadException("Failed to parse resource catalog JSON: ${error.message}", error)
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

    private fun parseColor(value: String): PixelColor {
        val hex = value.removePrefix("#")
        val argb = when (hex.length) {
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            else -> throw PixelResourceManifestLoadException(
                "Color '$value' must use #RRGGBB or #AARRGGBB",
            )
        }
        return PixelColor(argb)
    }
}
