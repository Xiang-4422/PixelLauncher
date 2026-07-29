package com.purride.pixellauncherv2.app

import android.os.Handler
import android.util.Log
import com.purride.pixellauncherv2.data.ContactDirectoryRepository
import com.purride.pixellauncherv2.data.ContactSearchRepository
import com.purride.pixellauncherv2.launcher.ContactEditorModel
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/**
 * 联系人域的运行时编排，与 [CallController]/[SmsController] 同构。
 *
 * 纯状态转移由 [LauncherStateTransitions] 提供；本类只承担命令式胶水
 * （仓库调用、线程）。目录不做进程级缓存：每次进入模块重读，如实反映外部改动。
 */
internal class ContactsController(
    private val contactDirectoryRepository: ContactDirectoryRepository,
    private val contactSearchRepository: ContactSearchRepository,
    private val backgroundExecutor: ExecutorService,
    private val mainHandler: Handler,
    private val host: Host,
) {

    /** 宿主（[MainActivity]）需要提供的钩子。 */
    interface Host {
        /** 共享的 Launcher 状态；联系人编排读写它，宿主持有真值。 */
        var state: LauncherState

        /** 把当前状态提交到 pixel-engine 渲染。 */
        fun render()

        /** Activity 仍存活（未销毁/未结束）时为 true，用于异步回调的有效性校验。 */
        fun isActive(): Boolean

        /** 全局状态栏临时消息（自动消失）。 */
        fun showStatusBarMessage(message: String)

        /** 申请 WRITE_CONTACTS（与 READ 同组，已授读时免弹窗补授）。 */
        fun requestContactsWritePermission()
    }

    /** 递增的加载代次，用于丢弃过期的异步结果。 */
    private var loadGeneration = 0L

    /**
     * 后台重读联系人目录并落地到状态。
     *
     * 无读取权限时同样落地（空列表 + hasPermission=false），联系人页据此渲染
     * 带授权入口的空态而不是永远转圈。
     */
    fun refreshContacts() {
        val generation = ++loadGeneration
        host.state = LauncherStateTransitions.beginContactsLoading(host.state)
        host.render()
        runInBackground {
            val hasPermission = contactDirectoryRepository.hasReadContactsPermission()
            val contacts = if (hasPermission) contactDirectoryRepository.loadContacts() else emptyList()
            mainHandler.post {
                if (!host.isActive() || generation != loadGeneration) {
                    return@post
                }
                host.state = LauncherStateTransitions.updateContacts(
                    state = host.state,
                    hasPermission = hasPermission,
                    contacts = contacts,
                )
                host.render()
            }
        }
    }

    /** 打开编辑器；[lookupKey] 为空串时是新建。 */
    fun openEditor(lookupKey: String) {
        host.state = LauncherStateTransitions.showContactEditor(host.state, lookupKey)
        host.render()
    }

    /** 放弃编辑：编辑既有联系人回详情，新建回联系人页。 */
    fun closeEditor() {
        host.state = LauncherStateTransitions.hideContactEditor(host.state)
        host.render()
    }

    /** 编辑器姓名草稿变更（TextField onChanged 回调）。 */
    fun updateEditorName(name: String) {
        host.state = LauncherStateTransitions.updateContactEditorName(host.state, name)
    }

    /** 编辑器号码草稿变更。 */
    fun updateEditorNumber(number: String) {
        host.state = LauncherStateTransitions.updateContactEditorNumber(host.state, number)
    }

    /**
     * 保存编辑器：新建 = 姓名 + 号码整体创建；编辑 = 改名（若变化）+ 追加号码（若填写）。
     *
     * 写库在后台线程；成功后丢弃 T9 快照并重读目录，让新联系人立即可被智能拨号命中。
     * 任何一步失败都在状态栏出声——静默失败等于骗用户"已保存"。
     */
    fun saveEditor() {
        val lookupKey = host.state.contactEditorLookupKey
        val name = host.state.contactEditorNameDraft.trim()
        val number = host.state.contactEditorNumberDraft.trim()
        val existing = host.state.contacts.firstOrNull { contact -> contact.lookupKey == lookupKey }
        if (!ContactEditorModel.canSave(name, number, hasExistingContact = existing != null)) {
            host.showStatusBarMessage(STATUS_INVALID_CONTACT)
            return
        }
        if (!contactDirectoryRepository.hasWriteContactsPermission()) {
            host.requestContactsWritePermission()
            return
        }
        runInBackground {
            val saved = if (existing == null) {
                contactDirectoryRepository.createContact(name, number)
            } else {
                val renamed = if (name != existing.displayName.trim()) {
                    contactDirectoryRepository.renameContact(existing.rawContactId, name)
                } else {
                    true
                }
                val added = if (number.isNotEmpty()) {
                    contactDirectoryRepository.addNumber(existing.rawContactId, number)
                } else {
                    true
                }
                renamed && added
            }
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                if (saved) {
                    contactSearchRepository.invalidate()
                    host.state = LauncherStateTransitions.hideContactEditor(host.state)
                    host.render()
                    refreshContacts()
                } else {
                    host.showStatusBarMessage(STATUS_SAVE_FAILED)
                }
            }
        }
    }

    /** 编辑器里删除一个既有号码；改号 = 删旧 + 加新。 */
    fun deleteNumber(dataId: Long) {
        if (!contactDirectoryRepository.hasWriteContactsPermission()) {
            host.requestContactsWritePermission()
            return
        }
        runInBackground {
            val deleted = contactDirectoryRepository.deleteNumber(dataId)
            mainHandler.post {
                if (!host.isActive()) {
                    return@post
                }
                if (deleted) {
                    contactSearchRepository.invalidate()
                    refreshContacts()
                } else {
                    host.showStatusBarMessage(STATUS_SAVE_FAILED)
                }
            }
        }
    }

    /** 打开联系人详情。 */
    fun openContact(lookupKey: String) {
        val nextState = LauncherStateTransitions.showContactDetail(host.state, lookupKey)
        if (nextState === host.state) {
            return
        }
        host.state = nextState
        host.render()
    }

    /** 关闭详情，回到联系人页。 */
    fun closeContact() {
        host.state = LauncherStateTransitions.hideContactDetail(host.state)
        host.render()
    }

    /**
     * 提交后台任务。宿主销毁时执行器被 shutdownNow：此后到达的异步回调
     * 再提交任务会抛 RejectedExecutionException，此时结果已无处落地。
     */
    private fun runInBackground(task: () -> Unit) {
        try {
            backgroundExecutor.execute {
                // 任务内部的未捕获异常会杀掉整个进程——对 Launcher 而言就是桌面
                // 消失。仓库层再怎么出错也只记日志，不允许带走宿主。
                runCatching(task).onFailure { error ->
                    Log.w(LOG_TAG, "background task failed", error)
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    private companion object {
        const val LOG_TAG = "ContactsController"
        const val STATUS_INVALID_CONTACT = "NAME AND NUMBER REQUIRED"
        const val STATUS_SAVE_FAILED = "SAVE FAILED"
    }
}
