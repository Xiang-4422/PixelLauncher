package com.purride.pixelui

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM compatibility corpus for BCP-47 canonicalization exposed by [PixelLocale]. */
class PixelLocaleCompatibilityCorpusTest {
    /** Locks canonical output for aliases, grandfathered tags, casing, extensions, and private use. */
    @Test
    fun canonicalLanguageTagCorpusMatchesThePublishedCompatibilitySnapshot() {
        /** Complete deterministic snapshot keeps failures readable across Java and Android runtimes. */
        val actualSnapshot = CorpusInputs.joinToString(separator = "\n") { input ->
            "$input => ${PixelLocale(input).languageTag}"
        }

        assertEquals(ExpectedSnapshot, actualSnapshot)
    }

    /** Owns the ordered inputs and reviewed JVM output without exposing test fixtures publicly. */
    private companion object {
        /** Inputs grouped by compatibility risk while preserving stable assertion order. */
        val CorpusInputs: List<String> = listOf(
            "iw",
            "in",
            "ji",
            "en-GB-oed",
            "i-ami",
            "i-bnn",
            "i-default",
            "i-enochian",
            "i-hak",
            "i-klingon",
            "i-lux",
            "i-mingo",
            "i-navajo",
            "i-pwn",
            "i-tao",
            "i-tay",
            "i-tsu",
            "sgn-BE-FR",
            "sgn-BE-NL",
            "sgn-CH-DE",
            "art-lojban",
            "cel-gaulish",
            "no-bok",
            "no-nyn",
            "zh-guoyu",
            "zh-hakka",
            "zh-min",
            "zh-min-nan",
            "zh-xiang",
            "ZH-hans-cn",
            "sr-latn-rs",
            "az-CYRL-az",
            "en-us-u-ca-gregory-nu-latn",
            "de-de-u-nu-latn-ca-gregory",
            "zh-hant-tw-u-nu-hanidec",
            "EN-us-X-FOO-Bar",
            "x-pixel-engine",
            "de-CH-x-phonebk",
        )

        /** Canonical output captured from the supported desktop JVM compatibility boundary. */
        val ExpectedSnapshot: String = """
            iw => he
            in => id
            ji => yi
            en-GB-oed => en-GB-x-oed
            i-ami => ami
            i-bnn => bnn
            i-default => en-x-i-default
            i-enochian => x-i-enochian
            i-hak => hak
            i-klingon => tlh
            i-lux => lb
            i-mingo => see-x-i-mingo
            i-navajo => nv
            i-pwn => pwn
            i-tao => tao
            i-tay => tay
            i-tsu => tsu
            sgn-BE-FR => sfb
            sgn-BE-NL => vgt
            sgn-CH-DE => sgg
            art-lojban => jbo
            cel-gaulish => xtg-x-cel-gaulish
            no-bok => nb
            no-nyn => nn
            zh-guoyu => cmn
            zh-hakka => hak
            zh-min => nan-x-zh-min
            zh-min-nan => nan
            zh-xiang => hsn
            ZH-hans-cn => zh-Hans-CN
            sr-latn-rs => sr-Latn-RS
            az-CYRL-az => az-Cyrl-AZ
            en-us-u-ca-gregory-nu-latn => en-US-u-ca-gregory-nu-latn
            de-de-u-nu-latn-ca-gregory => de-DE-u-ca-gregory-nu-latn
            zh-hant-tw-u-nu-hanidec => zh-Hant-TW-u-nu-hanidec
            EN-us-X-FOO-Bar => en-US-x-foo-bar
            x-pixel-engine => x-pixel-engine
            de-CH-x-phonebk => de-CH-x-phonebk
        """.trimIndent()
    }
}
