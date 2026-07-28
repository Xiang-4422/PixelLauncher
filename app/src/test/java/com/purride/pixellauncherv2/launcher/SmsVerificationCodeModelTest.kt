package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsVerificationCodeModelTest {

    @Test
    fun extractFindsChineseAndEnglishVerificationCodes() {
        assertEquals("839201", SmsVerificationCodeModel.extract("验证码 839201，10分钟有效"))
        assertEquals("4821", SmsVerificationCodeModel.extract("Your OTP is 4821."))
    }

    @Test
    fun extractFallsBackToFirstReasonableCode() {
        assertEquals("123456", SmsVerificationCodeModel.extract("Use 123456 to sign in"))
    }

    @Test
    fun extractFindsCodeBeforeKeyword() {
        assertEquals("123456", SmsVerificationCodeModel.extract("123456（动态验证码），请勿泄露"))
        assertEquals("998877", SmsVerificationCodeModel.extract("998877 is your verification code"))
    }

    @Test
    fun extractPrefersCodeAfterKeywordWhenBothSidesHaveDigits() {
        assertEquals(
            "839201",
            SmsVerificationCodeModel.extract("订单 20260728 的验证码 839201，10分钟内有效"),
        )
    }

    @Test
    fun extractIgnoresBlankOrTooShortNumbers() {
        assertNull(SmsVerificationCodeModel.extract(""))
        assertNull(SmsVerificationCodeModel.extract("ID 12"))
    }

    @Test
    fun displayCodeRequiresKeyword() {
        assertEquals("839201", SmsVerificationCodeModel.displayCode("验证码 839201，10分钟有效"))
        assertEquals("4419", SmsVerificationCodeModel.displayCode("您的取件码 4419，请到 3 号柜取件"))
        // 无关键词的普通数字（订单号/年份/金额）不再被标成 CODE。
        assertNull(SmsVerificationCodeModel.displayCode("订单 20260728 已发货"))
        assertNull(SmsVerificationCodeModel.displayCode("Use 123456 to sign in"))
        assertNull(SmsVerificationCodeModel.displayCode(""))
    }
}
