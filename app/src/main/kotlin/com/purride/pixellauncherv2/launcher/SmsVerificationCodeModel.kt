package com.purride.pixellauncherv2.launcher

object SmsVerificationCodeModel {

    /**
     * 宽松提取：优先关键词附近的数字，无关键词时回退取首个 4-8 位数字。
     * 供"点按复制"这类用户显式操作使用——误判成本低（复制错了再复制正文）。
     */
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

    /**
     * 严格提取：必须命中验证码关键词才返回。
     * 供"CODE 标签"这类被动展示使用——否则订单号、年份等任意 4-8 位数字
     * 都会被标成验证码，噪声很大。
     */
    fun displayCode(body: String): String? {
        val normalized = body.trim()
        if (normalized.isEmpty()) return null
        if (!keywordPattern.containsMatchIn(normalized)) return null
        return extract(normalized)
    }

    private val keywordPattern = Regex(
        pattern = "(code|otp|pin|verify|verification|验证码|校验码|动态码|取件码|提取码|动态密码)",
        option = RegexOption.IGNORE_CASE,
    )
    private val codePattern = Regex("""(?<!\d)\d{4,8}(?!\d)""")
}
