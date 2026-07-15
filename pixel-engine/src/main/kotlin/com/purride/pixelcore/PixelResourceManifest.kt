package com.purride.pixelcore

/** 资源 manifest 或 catalog JSON 解析失败。 */
public class PixelResourceManifestLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** manifest 中的一张 bitmap 资源定义，并保留旧 data class 的二进制契约。 */
public class PixelBitmapResourceDefinition @JvmOverloads public constructor(
    /** catalog 内稳定且非空的资源 id。 */
    public val id: String,
    /** 相对资源根目录的安全路径。 */
    public val path: String,
    /** 可选的编码文件 SHA-256；加载器必须在解码前校验。 */
    public val sha256: String? = null,
) {
    init {
        requireResourceId(id, "bitmap id")
        requireResourcePath(path, "bitmap path")
        requireOptionalSha256(sha256, "bitmap '$id' sha256")
    }

    /** 保留旧 data class 的第一个解构槽位。 */
    public operator fun component1(): String = id

    /** 保留旧 data class 的第二个解构槽位。 */
    public operator fun component2(): String = path

    /** 新增 checksum 解构槽位。 */
    public operator fun component3(): String? = sha256

    /** 保留旧两参数 copy 描述符，并默认延续当前 checksum。 */
    public fun copy(
        id: String = this.id,
        path: String = this.path,
    ): PixelBitmapResourceDefinition = PixelBitmapResourceDefinition(id, path, sha256)

    /** 按资源定义的全部字段比较。 */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelBitmapResourceDefinition) return false
        return id == other.id && path == other.path && sha256 == other.sha256
    }

    /** 对无 checksum 的旧实例保持原 data class 哈希。 */
    override fun hashCode(): Int {
        /** 旧 id/path 字段生成的哈希。 */
        val legacyHash = 31 * id.hashCode() + path.hashCode()
        return sha256?.let { checksum -> 31 * legacyHash + checksum.hashCode() } ?: legacyHash
    }

    /** 对无 checksum 的旧实例保持原 data class 文本。 */
    override fun toString(): String {
        return if (sha256 == null) {
            "PixelBitmapResourceDefinition(id=$id, path=$path)"
        } else {
            "PixelBitmapResourceDefinition(id=$id, path=$path, sha256=$sha256)"
        }
    }
}

/** manifest 中的一张 sprite sheet 定义，并保留旧 data class 的二进制契约。 */
public class PixelSpriteSheetResourceDefinition @JvmOverloads public constructor(
    /** catalog 内稳定且非空的资源 id。 */
    public val id: String,
    /** 相对资源根目录的 sheet JSON 路径。 */
    public val path: String,
    /** 被该 sheet 引用的 bitmap 资源 id。 */
    public val bitmap: String,
    /** 可选的 sheet JSON SHA-256。 */
    public val sha256: String? = null,
) {
    init {
        requireResourceId(id, "sprite sheet id")
        requireResourcePath(path, "sprite sheet path")
        requireResourceId(bitmap, "sprite sheet bitmap")
        requireOptionalSha256(sha256, "sprite sheet '$id' sha256")
    }

    /** 保留旧 data class 的第一个解构槽位。 */
    public operator fun component1(): String = id

    /** 保留旧 data class 的第二个解构槽位。 */
    public operator fun component2(): String = path

    /** 保留旧 data class 的第三个解构槽位。 */
    public operator fun component3(): String = bitmap

    /** 新增 checksum 解构槽位。 */
    public operator fun component4(): String? = sha256

    /** 保留旧三参数 copy 描述符，并默认延续当前 checksum。 */
    public fun copy(
        id: String = this.id,
        path: String = this.path,
        bitmap: String = this.bitmap,
    ): PixelSpriteSheetResourceDefinition = PixelSpriteSheetResourceDefinition(id, path, bitmap, sha256)

    /** 按资源定义的全部字段比较。 */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelSpriteSheetResourceDefinition) return false
        return id == other.id && path == other.path && bitmap == other.bitmap && sha256 == other.sha256
    }

    /** 对无 checksum 的旧实例保持原 data class 哈希。 */
    override fun hashCode(): Int {
        /** 旧 id/path/bitmap 字段生成的哈希。 */
        val legacyHash = 31 * (31 * id.hashCode() + path.hashCode()) + bitmap.hashCode()
        return sha256?.let { checksum -> 31 * legacyHash + checksum.hashCode() } ?: legacyHash
    }

    /** 对无 checksum 的旧实例保持原 data class 文本。 */
    override fun toString(): String {
        return if (sha256 == null) {
            "PixelSpriteSheetResourceDefinition(id=$id, path=$path, bitmap=$bitmap)"
        } else {
            "PixelSpriteSheetResourceDefinition(id=$id, path=$path, bitmap=$bitmap, sha256=$sha256)"
        }
    }
}

/** catalog 中的命名颜色资源。 */
public data class PixelColorResourceDefinition(
    /** catalog 内稳定且非空的资源 id。 */
    public val id: String,
    /** 已解析为 ARGB 的不可变颜色。 */
    val color: PixelColor,
) {
    init {
        requireResourceId(id, "color id")
    }
}

/** catalog 中的字形包资源，并保留旧 data class 的二进制契约。 */
public class PixelFontResourceDefinition @JvmOverloads public constructor(
    /** catalog 内稳定且非空的资源 id。 */
    public val id: String,
    /** 相对资源根目录的 glyph manifest 路径。 */
    public val manifestPath: String,
    /** 相对资源根目录的 glyph 二进制路径。 */
    public val binaryPath: String,
    /** 可选的 glyph manifest SHA-256。 */
    public val manifestSha256: String? = null,
    /** 可选的 glyph 二进制 SHA-256。 */
    public val binarySha256: String? = null,
) {
    init {
        requireResourceId(id, "font id")
        requireResourcePath(manifestPath, "font manifestPath")
        requireResourcePath(binaryPath, "font binaryPath")
        requireOptionalSha256(manifestSha256, "font '$id' manifestSha256")
        requireOptionalSha256(binarySha256, "font '$id' binarySha256")
    }

    /** 保留旧 data class 的第一个解构槽位。 */
    public operator fun component1(): String = id

    /** 保留旧 data class 的第二个解构槽位。 */
    public operator fun component2(): String = manifestPath

    /** 保留旧 data class 的第三个解构槽位。 */
    public operator fun component3(): String = binaryPath

    /** 新增 manifest checksum 解构槽位。 */
    public operator fun component4(): String? = manifestSha256

    /** 新增二进制 checksum 解构槽位。 */
    public operator fun component5(): String? = binarySha256

    /** 保留旧三参数 copy 描述符，并默认延续当前 checksum。 */
    public fun copy(
        id: String = this.id,
        manifestPath: String = this.manifestPath,
        binaryPath: String = this.binaryPath,
    ): PixelFontResourceDefinition = PixelFontResourceDefinition(
        id = id,
        manifestPath = manifestPath,
        binaryPath = binaryPath,
        manifestSha256 = manifestSha256,
        binarySha256 = binarySha256,
    )

    /** 按资源定义的全部字段比较。 */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelFontResourceDefinition) return false
        return id == other.id &&
            manifestPath == other.manifestPath &&
            binaryPath == other.binaryPath &&
            manifestSha256 == other.manifestSha256 &&
            binarySha256 == other.binarySha256
    }

    /** 对无 checksum 的旧实例保持原 data class 哈希。 */
    override fun hashCode(): Int {
        /** 旧 id/manifest/binary 字段生成的哈希。 */
        val legacyHash = 31 * (31 * id.hashCode() + manifestPath.hashCode()) + binaryPath.hashCode()
        if (manifestSha256 == null && binarySha256 == null) return legacyHash
        /** 包含两个可选 checksum 的新哈希。 */
        var result = legacyHash
        result = 31 * result + (manifestSha256?.hashCode() ?: 0)
        result = 31 * result + (binarySha256?.hashCode() ?: 0)
        return result
    }

    /** 对无 checksum 的旧实例保持原 data class 文本。 */
    override fun toString(): String {
        return if (manifestSha256 == null && binarySha256 == null) {
            "PixelFontResourceDefinition(id=$id, manifestPath=$manifestPath, binaryPath=$binaryPath)"
        } else {
            "PixelFontResourceDefinition(id=$id, manifestPath=$manifestPath, binaryPath=$binaryPath, " +
                "manifestSha256=$manifestSha256, binarySha256=$binarySha256)"
        }
    }
}

/** bitmap 与 sprite sheet 的基础资源清单。 */
public data class PixelResourceManifest(
    /** 按声明顺序保存的 bitmap 定义。 */
    val bitmaps: List<PixelBitmapResourceDefinition>,
    /** 按声明顺序保存的 sprite sheet 定义。 */
    val spriteSheets: List<PixelSpriteSheetResourceDefinition>,
    /** 资源协议版本；当前只接受 1 和 2。 */
    val version: Int = 1,
    /** 只允许字符串键值且受数量限制的附加信息。 */
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(version in 1..2) { "unsupported resource manifest version $version" }
        require(bitmaps.size <= PixelResourceSafetyLimits.MaxEntries) {
            "bitmap count ${bitmaps.size} exceeds ${PixelResourceSafetyLimits.MaxEntries}"
        }
        require(spriteSheets.size <= PixelResourceSafetyLimits.MaxEntries) {
            "sprite sheet count ${spriteSheets.size} exceeds ${PixelResourceSafetyLimits.MaxEntries}"
        }
        require(metadata.size <= PixelResourceSafetyLimits.MaxMetadataEntries) {
            "metadata count ${metadata.size} exceeds ${PixelResourceSafetyLimits.MaxMetadataEntries}"
        }
        firstDuplicate(bitmaps.map { definition -> definition.id })?.let { duplicate ->
            throw IllegalArgumentException("duplicate bitmap id '$duplicate'")
        }
        firstDuplicate(spriteSheets.map { definition -> definition.id })?.let { duplicate ->
            throw IllegalArgumentException("duplicate sprite sheet id '$duplicate'")
        }
        /** 能被 sprite sheet 引用的 bitmap id 集合。 */
        val bitmapIds = bitmaps.mapTo(linkedSetOf()) { definition -> definition.id }
        spriteSheets.forEach { sheet ->
            require(sheet.bitmap in bitmapIds) {
                "sprite sheet '${sheet.id}' references missing bitmap '${sheet.bitmap}'"
            }
        }
    }

    /** 返回输入中第一个重复 id。 */
    private fun firstDuplicate(ids: List<String>): String? {
        /** 已观察到的资源 id。 */
        val seen = mutableSetOf<String>()
        return ids.firstOrNull { id -> !seen.add(id) }
    }
}

/** 完整资源目录，在基础 manifest 之外增加颜色和字体资源。 */
public data class PixelResourceCatalog(
    /** bitmap 与 sprite sheet 基础资源。 */
    val resources: PixelResourceManifest,
    /** 命名颜色资源。 */
    val colors: List<PixelColorResourceDefinition>,
    /** 字形包资源。 */
    val fonts: List<PixelFontResourceDefinition>,
) {
    init {
        require(resources.version == 2 || (colors.isEmpty() && fonts.isEmpty())) {
            "resource manifest version 2 is required for colors or fonts"
        }
        require(colors.size <= PixelResourceSafetyLimits.MaxEntries) {
            "color count ${colors.size} exceeds ${PixelResourceSafetyLimits.MaxEntries}"
        }
        require(fonts.size <= PixelResourceSafetyLimits.MaxEntries) {
            "font count ${fonts.size} exceeds ${PixelResourceSafetyLimits.MaxEntries}"
        }
        /** 所有资源类型共享的全局 id 序列。 */
        val ids = buildList {
            addAll(resources.bitmaps.map { definition -> definition.id })
            addAll(resources.spriteSheets.map { definition -> definition.id })
            addAll(colors.map { definition -> definition.id })
            addAll(fonts.map { definition -> definition.id })
        }
        /** 首个跨类型重复 id。 */
        val duplicate = ids.groupingBy { id -> id }.eachCount().entries
            .firstOrNull { entry -> entry.value > 1 }
            ?.key
        require(duplicate == null) { "duplicate resource id '$duplicate'" }
    }
}

/** 严格有界 JSON 解析器，用于读取 pixel-engine 资源 manifest。 */
public object PixelResourceManifestJsonLoader {
    /** 解析基础 bitmap/sprite manifest。 */
    public fun parse(json: String): PixelResourceManifest = parse(json, expectedSha256 = null)

    /** 在解析前校验 JSON 的外部 SHA-256，再解析基础 manifest。 */
    public fun parse(json: String, expectedSha256: String?): PixelResourceManifest {
        return wrapManifestError("resource manifest") {
            /** manifest 的原始 UTF-8 字节。 */
            val bytes = json.toByteArray(Charsets.UTF_8)
            bytes.requireSha256(expectedSha256, "resource manifest")
            /** 经过结构、深度和重复 key 校验的根对象。 */
            val root = PixelBoundedJson.parseObject(
                source = json,
                limits = PixelJsonLimits(maxInputChars = PixelResourceSafetyLimits.MaxJsonChars),
            )
            parseManifestObject(root)
        }
    }

    /** 解析完整 catalog，包含 colors 和 fonts。 */
    public fun parseCatalog(json: String): PixelResourceCatalog =
        parseCatalog(json, expectedSha256 = null)

    /** 在解析前校验 JSON 的外部 SHA-256，再解析完整 catalog。 */
    public fun parseCatalog(json: String, expectedSha256: String?): PixelResourceCatalog {
        return wrapManifestError("resource catalog") {
            /** catalog 的原始 UTF-8 字节。 */
            val bytes = json.toByteArray(Charsets.UTF_8)
            bytes.requireSha256(expectedSha256, "resource catalog")
            /** 经过结构、深度和重复 key 校验的根对象。 */
            val root = PixelBoundedJson.parseObject(
                source = json,
                limits = PixelJsonLimits(maxInputChars = PixelResourceSafetyLimits.MaxJsonChars),
            )
            /** catalog 内的基础资源清单。 */
            val resources = parseManifestObject(root)
            /** 有界颜色定义。 */
            val colors = root.optionalArray("colors")
                ?.requireObjects("colors")
                ?.also { definitions -> requireEntryLimit("colors", definitions.size) }
                ?.map { item ->
                    PixelColorResourceDefinition(
                        id = item.requireString("id"),
                        color = parseColor(item.requireString("value")),
                    )
                }
                .orEmpty()
            /** 有界字体定义。 */
            val fonts = root.optionalArray("fonts")
                ?.requireObjects("fonts")
                ?.also { definitions -> requireEntryLimit("fonts", definitions.size) }
                ?.map { item ->
                    PixelFontResourceDefinition(
                        id = item.requireString("id"),
                        manifestPath = item.requireString("manifest"),
                        binaryPath = item.requireString("binary"),
                        manifestSha256 = item.optionalString("manifestSha256"),
                        binarySha256 = item.optionalString("binarySha256"),
                    )
                }
                .orEmpty()
            PixelResourceCatalog(resources = resources, colors = colors, fonts = fonts)
        }
    }

    /** 从已校验根对象构建基础 manifest。 */
    private fun parseManifestObject(root: PixelJsonObject): PixelResourceManifest {
        /** 未声明时保持第一版兼容的协议版本。 */
        val version = root.optionalInt("version") ?: 1
        /** 可选字符串 metadata。 */
        val metadata = root.optionalObject("metadata")
            ?.requireStringMap("metadata")
            .orEmpty()
        require(metadata.size <= PixelResourceSafetyLimits.MaxMetadataEntries) {
            "metadata count ${metadata.size} exceeds ${PixelResourceSafetyLimits.MaxMetadataEntries}"
        }
        /** 有界 bitmap 定义。 */
        val bitmaps = root.optionalArray("bitmaps")
            ?.requireObjects("bitmaps")
            ?.also { definitions -> requireEntryLimit("bitmaps", definitions.size) }
            ?.map { item ->
                PixelBitmapResourceDefinition(
                    id = item.requireString("id"),
                    path = item.requireString("path"),
                    sha256 = item.optionalString("sha256"),
                )
            }
            .orEmpty()
        /** 有界 sprite sheet 定义。 */
        val spriteSheets = root.optionalArray("spriteSheets")
            ?.requireObjects("spriteSheets")
            ?.also { definitions -> requireEntryLimit("spriteSheets", definitions.size) }
            ?.map { item ->
                PixelSpriteSheetResourceDefinition(
                    id = item.requireString("id"),
                    path = item.requireString("path"),
                    bitmap = item.requireString("bitmap"),
                    sha256 = item.optionalString("sha256"),
                )
            }
            .orEmpty()
        return PixelResourceManifest(
            bitmaps = bitmaps,
            spriteSheets = spriteSheets,
            version = version,
            metadata = metadata.toMap(),
        )
    }

    /** 校验单个资源数组数量。 */
    private fun requireEntryLimit(label: String, count: Int) {
        require(count <= PixelResourceSafetyLimits.MaxEntries) {
            "$label count $count exceeds ${PixelResourceSafetyLimits.MaxEntries}"
        }
    }

    /** 解析 #RRGGBB 或 #AARRGGBB，拒绝符号和溢出。 */
    private fun parseColor(value: String): PixelColor {
        /** 去掉可选前缀后的十六进制文本。 */
        val hex = value.removePrefix("#")
        require(hex.matches(Regex("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}"))) {
            "Color '$value' must use #RRGGBB or #AARRGGBB"
        }
        /** 统一为 ARGB 的 32 位颜色值。 */
        val argb = when (hex.length) {
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            else -> hex.toLong(16).toInt()
        }
        return PixelColor(argb)
    }

    /** 把所有实现异常统一包装为稳定的公开解析异常。 */
    private inline fun <T> wrapManifestError(label: String, block: () -> T): T {
        return try {
            block()
        } catch (error: PixelResourceManifestLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PixelResourceManifestLoadException(
                "Failed to parse $label JSON: ${error.message}",
                error,
            )
        }
    }
}

/** 校验资源 id 的长度和空白规则。 */
private fun requireResourceId(value: String, label: String) {
    require(value.isNotBlank()) { "$label must not be blank" }
    require(value.length <= 256) { "$label exceeds 256 chars" }
    require(value.none(Char::isISOControl)) { "$label contains control characters" }
}

/** 校验相对资源路径，拒绝绝对路径和父目录穿越。 */
private fun requireResourcePath(value: String, label: String) {
    require(value.isNotBlank()) { "$label must not be blank" }
    require(value.length <= 1024) { "$label exceeds 1024 chars" }
    require(!value.startsWith('/') && !value.startsWith('\\')) { "$label must be relative" }
    /** 使用统一分隔符拆分后的路径段。 */
    val segments = value.replace('\\', '/').split('/')
    require(segments.none { segment -> segment.isEmpty() || segment == "." || segment == ".." }) {
        "$label contains an unsafe path segment"
    }
    require(value.none(Char::isISOControl)) { "$label contains control characters" }
}
