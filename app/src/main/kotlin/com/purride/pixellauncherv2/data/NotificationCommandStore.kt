package com.purride.pixellauncherv2.data

import android.app.PendingIntent

data class NotificationCommand(
    val key: String,
    val sourceId: String,
    val contentIntent: PendingIntent?,
    val actions: List<PendingIntent?> = emptyList(),
)

object NotificationCommandStore {
    private val lock = Any()
    private var commandsByKey: Map<String, NotificationCommand> = emptyMap()

    fun update(commands: List<NotificationCommand>) {
        synchronized(lock) {
            commandsByKey = commands
                .filter { command -> command.key.isNotBlank() }
                .associateBy(NotificationCommand::key)
        }
    }

    fun command(key: String): NotificationCommand? = synchronized(lock) {
        commandsByKey[key]
    }

    fun sendContent(key: String): Boolean {
        val intent = command(key)?.contentIntent ?: return false
        return send(intent)
    }

    fun sendAction(key: String, actionIndex: Int): Boolean {
        val intent = command(key)?.actions?.getOrNull(actionIndex) ?: return false
        return send(intent)
    }

    fun clear() {
        synchronized(lock) {
            commandsByKey = emptyMap()
        }
    }

    private fun send(intent: PendingIntent): Boolean {
        return runCatching {
            intent.send()
        }.isSuccess
    }
}
