package com.purride.pixellauncherv2.launcher

object SmsVerificationCodeModel {

    fun extract(body: String): String? {
        val normalized = body.trim()
        if (normalized.isEmpty()) return null
        val keywordMatch = keywordPattern.find(normalized)
        if (keywordMatch != null) {
            // 优先取关键词之后最近的数字；"123456（动态验证码）"这类验证码在关键词
            // 之前的格式，关键词后找不到数字时回退到全文查找。
            codePattern.find(normalized, keywordMatch.range.last.coerceAtLeast(0))
                ?.let { return it.value }
        }
        return codePattern.find(normalized)?.value
    }

    private val keywordPattern = Regex(
        pattern = "(code|otp|pin|verify|verification|验证码|校验码|动态码)",
        option = RegexOption.IGNORE_CASE,
    )
    private val codePattern = Regex("""(?<!\d)\d{4,8}(?!\d)""")
}
