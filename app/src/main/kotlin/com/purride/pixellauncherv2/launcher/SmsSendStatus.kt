package com.purride.pixellauncherv2.launcher

/**
 * 详情页输入区的发送状态。
 *
 * 只承载“持续状态”（发送中/失败待处理）；复制反馈这类一次性提示
 * 走全局状态栏临时消息通道，不在此列。文案由渲染层映射。
 */
enum class SmsSendStatus {
    NONE,
    SENDING,
    FAILED,
}
