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
    val normalizedAlias: String,
    val pinyinFull: String,
    val pinyinInitial: String,
    val sortKey: String,
    val letterIndex: Int,
)

object DrawerSearchSupport {

    fun buildMetadata(appEntry: AppEntry): DrawerSearchMetadata {
        return buildMetadata(
            label = appEntry.label,
            packageName = appEntry.packageName,
        )
    }

    fun buildMetadata(label: String, packageName: String): DrawerSearchMetadata {
        val normalizedLabel = normalizeForSearch(label)
        val normalizedAlias = normalizeForSearch(packageName.substringAfterLast('.'))
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
            normalizedAlias = normalizedAlias,
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
