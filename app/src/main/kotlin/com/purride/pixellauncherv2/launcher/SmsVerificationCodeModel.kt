package com.purride.pixellauncherv2.launcher

object SmsVerificationCodeModel {

    fun extract(body: String): String? {
        val normalized = body.trim()
        if (normalized.isEmpty()) return null
        val keywordMatch = keywordPattern.find(normalized)
        if (keywordMatch != null) {
            return codePattern.find(normalized, keywordMatch.range.last.coerceAtLeast(0))
                ?.value
        }
        return codePattern.find(normalized)?.value
    }

    private val keywordPattern = Regex(
        pattern = "(code|otp|pin|verify|verification|验证码|校验码|动态码)",
        option = RegexOption.IGNORE_CASE,
    )
    private val codePattern = Regex("""(?<!\d)\d{4,8}(?!\d)""")
}
