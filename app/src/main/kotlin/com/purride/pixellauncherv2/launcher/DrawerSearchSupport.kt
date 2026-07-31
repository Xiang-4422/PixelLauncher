package com.purride.pixellauncherv2.launcher

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import java.util.Locale
import kotlin.math.absoluteValue

data class DrawerSearchMetadata(
    val normalizedLabel: String,
    val normalizedEnglishLabel: String,
    val normalizedAlias: String,
    val normalizedUserAliases: List<String>,
    val normalizedPackage: String,
    val normalizedActivity: String,
    val pinyinFull: String,
    val pinyinInitial: String,
    val sortKey: String,
    val letterIndex: Int,
)

object DrawerSearchSupport {

    fun buildMetadata(appEntry: AppEntry): DrawerSearchMetadata {
        return buildMetadata(
            label = appEntry.label,
            englishLabel = appEntry.englishLabel,
            aliases = appEntry.aliases,
            packageName = appEntry.packageName,
            activityName = appEntry.activityName,
        )
    }

    fun buildMetadata(
        label: String,
        englishLabel: String = "",
        aliases: List<String> = emptyList(),
        packageName: String,
        activityName: String = "",
    ): DrawerSearchMetadata {
        val normalizedLabel = normalizeForSearch(label)
        val normalizedEnglishLabel = normalizeForSearch(englishLabel)
        val normalizedAlias = normalizeForSearch(packageName.substringAfterLast('.'))
        val normalizedUserAliases = aliases
            .map(::normalizeForSearch)
            .filter { it.isNotEmpty() }
            .distinct()
        val normalizedPackage = normalizeForSearch(packageName)
        val normalizedActivity = normalizeForSearch(activityName.substringAfterLast('.'))
        val pinyin = toPinyin(label)
        val sortKey = when {
            pinyin.full.isNotEmpty() -> pinyin.full
            normalizedLabel.isNotEmpty() -> normalizedLabel
            normalizedAlias.isNotEmpty() -> normalizedAlias
            else -> ""
        }
        val letterIndex = resolveLetterIndex(
            rawLabel = label,
            pinyinInitial = pinyin.initial,
            sortKey = sortKey,
        )
        return DrawerSearchMetadata(
            normalizedLabel = normalizedLabel,
            normalizedEnglishLabel = normalizedEnglishLabel,
            normalizedAlias = normalizedAlias,
            normalizedUserAliases = normalizedUserAliases,
            normalizedPackage = normalizedPackage,
            normalizedActivity = normalizedActivity,
            pinyinFull = pinyin.full,
            pinyinInitial = pinyin.initial,
            sortKey = sortKey,
            letterIndex = letterIndex,
        )
    }

    fun letterIndexForLabel(label: String): Int {
        return buildMetadata(label = label, packageName = "").letterIndex
    }

    fun normalizeForSearch(value: String): String {
        if (value.isBlank()) {
            return ""
        }
        return value
            .lowercase(Locale.getDefault())
            .replace(searchNoiseRegex, "")
    }

    fun matchSourceLabel(appEntry: AppEntry, query: String): String? {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) {
            return null
        }
        val metadata = buildMetadata(appEntry)
        return matchSourceLabel(
            normalizedQuery = normalizedQuery,
            metadata = metadata,
        )
    }

    /**
     * 按 Drawer 的搜索规则计算匹配等级；数值越小表示结果越相关，`null` 表示不匹配。
     *
     * 该方法供 Drawer 之外的应用选择页面复用，避免白名单等页面另建一套搜索语义。
     */
    fun searchScore(
        metadata: DrawerSearchMetadata,
        query: String,
    ): Int? {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) {
            return 0
        }
        return calculateSearchScore(
            metadata = metadata,
            normalizedQuery = normalizedQuery,
        )
    }

    /** 使用已经归一化的查询文本计算 Drawer 搜索匹配等级。 */
    fun searchScoreForNormalizedQuery(
        metadata: DrawerSearchMetadata,
        normalizedQuery: String,
    ): Int? {
        if (normalizedQuery.isEmpty()) {
            return 0
        }
        return calculateSearchScore(
            metadata = metadata,
            normalizedQuery = normalizedQuery,
        )
    }

    private fun matchSourceLabel(
        normalizedQuery: String,
        metadata: DrawerSearchMetadata,
    ): String? {
        return when {
            metadata.normalizedLabel.matchesQuery(normalizedQuery) ||
                metadata.normalizedEnglishLabel.matchesQuery(normalizedQuery) -> null

            metadata.normalizedUserAliases.any { it.matchesQuery(normalizedQuery) } -> "ALIAS"

            metadata.pinyinFull.matchesQuery(normalizedQuery) ||
                metadata.pinyinInitial.matchesQuery(normalizedQuery) -> "PINYIN"

            metadata.normalizedPackage.matchesQuery(normalizedQuery) ||
                metadata.normalizedAlias.matchesQuery(normalizedQuery) -> "PKG"

            metadata.normalizedActivity.matchesQuery(normalizedQuery) -> "ACT"

            else -> null
        }
    }

    /** Drawer 统一使用的精确、前缀、拼音及包含匹配优先级。 */
    private fun calculateSearchScore(
        metadata: DrawerSearchMetadata,
        normalizedQuery: String,
    ): Int? {
        return when {
            metadata.normalizedLabel == normalizedQuery ||
                metadata.normalizedEnglishLabel == normalizedQuery ||
                metadata.normalizedAlias == normalizedQuery ||
                metadata.normalizedUserAliases.any { it == normalizedQuery } ||
                metadata.normalizedPackage == normalizedQuery ||
                metadata.normalizedActivity == normalizedQuery ||
                metadata.pinyinFull == normalizedQuery ||
                metadata.pinyinInitial == normalizedQuery -> 0

            metadata.normalizedLabel.startsWith(normalizedQuery) -> 1
            metadata.normalizedEnglishLabel.startsWith(normalizedQuery) -> 1
            metadata.normalizedUserAliases.any { it.startsWith(normalizedQuery) } -> 1
            metadata.normalizedAlias.startsWith(normalizedQuery) -> 2
            metadata.normalizedPackage.startsWith(normalizedQuery) -> 2
            metadata.normalizedActivity.startsWith(normalizedQuery) -> 2
            metadata.pinyinFull.startsWith(normalizedQuery) -> 3
            metadata.pinyinInitial.startsWith(normalizedQuery) -> 4
            metadata.normalizedLabel.contains(normalizedQuery) ||
                metadata.normalizedEnglishLabel.contains(normalizedQuery) ||
                metadata.normalizedUserAliases.any { it.contains(normalizedQuery) } ||
                metadata.normalizedAlias.contains(normalizedQuery) ||
                metadata.normalizedPackage.contains(normalizedQuery) ||
                metadata.normalizedActivity.contains(normalizedQuery) ||
                metadata.pinyinFull.contains(normalizedQuery) ||
                metadata.pinyinInitial.contains(normalizedQuery) -> 5

            else -> null
        }
    }

    private fun toPinyin(label: String): PinyinTokens {
        if (label.isBlank()) {
            return PinyinTokens()
        }

        val full = StringBuilder(label.length * 2)
        val initial = StringBuilder(label.length)
        var inAsciiToken = false

        label.forEach { char ->
            when {
                char.isWhitespace() || isSearchNoise(char) -> {
                    inAsciiToken = false
                }

                char.isAsciiAlphaNumeric() -> {
                    val normalizedChar = char.lowercaseChar()
                    full.append(normalizedChar)
                    if (!inAsciiToken) {
                        initial.append(normalizedChar)
                        inAsciiToken = true
                    }
                }

                else -> {
                    inAsciiToken = false
                    val pinyin = PinyinHelper.toHanyuPinyinStringArray(char, pinyinFormat)
                        ?.firstOrNull()
                        ?.lowercase(Locale.getDefault())
                        .orEmpty()
                    if (pinyin.isNotEmpty()) {
                        full.append(pinyin)
                        initial.append(pinyin.first())
                    } else if (char.isLetterOrDigit()) {
                        full.append(char.lowercaseChar())
                    }
                }
            }
        }
        return PinyinTokens(
            full = normalizeForSearch(full.toString()),
            initial = normalizeForSearch(initial.toString()),
        )
    }

    private fun resolveLetterIndex(
        rawLabel: String,
        pinyinInitial: String,
        sortKey: String,
    ): Int {
        val latinFromLabel = rawLabel.trimStart()
            .firstOrNull { !it.isWhitespace() && !isSearchNoise(it) }
            ?.uppercaseChar()
            ?.takeIf { it in 'A'..'Z' }
        if (latinFromLabel != null) {
            return latinFromLabel - 'A'
        }

        val latinFromPinyinInitial = pinyinInitial.firstOrNull()
            ?.uppercaseChar()
            ?.takeIf { it in 'A'..'Z' }
        if (latinFromPinyinInitial != null) {
            return latinFromPinyinInitial - 'A'
        }

        val latinFromSortKey = sortKey.firstOrNull()
            ?.uppercaseChar()
            ?.takeIf { it in 'A'..'Z' }
        if (latinFromSortKey != null) {
            return latinFromSortKey - 'A'
        }

        val fallbackChar = rawLabel.trimStart()
            .firstOrNull { !it.isWhitespace() && !isSearchNoise(it) }
            ?: return DrawerAlphaIndexModel.lastLetterIndex
        return (fallbackChar.code.absoluteValue % DrawerAlphaIndexModel.letterCount)
    }

    private fun isSearchNoise(char: Char): Boolean {
        return searchNoiseRegex.matches(char.toString())
    }

    private fun Char.isAsciiAlphaNumeric(): Boolean {
        return this in 'a'..'z' ||
            this in 'A'..'Z' ||
            this in '0'..'9'
    }

    private fun String.matchesQuery(normalizedQuery: String): Boolean {
        return isNotEmpty() && contains(normalizedQuery)
    }

    private data class PinyinTokens(
        val full: String = "",
        val initial: String = "",
    )

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }
    private val searchNoiseRegex = Regex("[\\s\\p{Punct}_]+")
}
