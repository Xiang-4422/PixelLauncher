package com.purride.pixellauncherv2.launcher

/**
 * 按会话保存未发送草稿。
 *
 * 仅内存持有（进程存活期间有效），只在主线程读写；
 * 键为会话的 conversationKey，与消息流的会话归组口径一致。
 */
class SmsDraftStore {

    private val drafts = mutableMapOf<String, String>()

    /** 进入会话时恢复草稿；深链等场景的预填内容优先于已存草稿。 */
    fun restore(conversationKey: String, prefilled: String): String {
        if (prefilled.isNotBlank()) {
            return prefilled
        }
        return drafts[conversationKey].orEmpty()
    }

    /** 输入变化时记录草稿；空白草稿视为清除，不留空条目。 */
    fun update(conversationKey: String, draft: String) {
        if (conversationKey.isBlank()) {
            return
        }
        if (draft.isBlank()) {
            drafts.remove(conversationKey)
        } else {
            drafts[conversationKey] = draft
        }
    }

    /** 发送成功后清除对应会话的草稿。 */
    fun clear(conversationKey: String) {
        drafts.remove(conversationKey)
    }
}
