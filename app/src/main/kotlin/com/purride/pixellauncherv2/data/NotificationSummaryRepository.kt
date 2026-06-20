package com.purride.pixellauncherv2.data

import com.purride.pixellauncherv2.launcher.NotificationSummary

class NotificationSummaryRepository(
    private val store: NotificationSummaryStore = NotificationSummaryStore,
) {
    private var listener: ((NotificationSummary) -> Unit)? = null

    fun start(onSummaryChanged: (NotificationSummary) -> Unit) {
        stop()
        listener = onSummaryChanged
        store.addListener(onSummaryChanged)
        onSummaryChanged(store.current())
    }

    fun stop() {
        listener?.let(store::removeListener)
        listener = null
    }

    fun current(): NotificationSummary = store.current()
}

object NotificationSummaryStore {
    private val lock = Any()
    private var summary: NotificationSummary = NotificationSummary(count = 0, text = "")
    private val listeners = linkedSetOf<(NotificationSummary) -> Unit>()

    fun current(): NotificationSummary = synchronized(lock) { summary }

    fun update(nextSummary: NotificationSummary) {
        val targets = synchronized(lock) {
            summary = nextSummary
            listeners.toList()
        }
        targets.forEach { listener -> listener(nextSummary) }
    }

    fun addListener(listener: (NotificationSummary) -> Unit) {
        synchronized(lock) {
            listeners += listener
        }
    }

    fun removeListener(listener: (NotificationSummary) -> Unit) {
        synchronized(lock) {
            listeners -= listener
        }
    }

    fun resetForTests() {
        synchronized(lock) {
            summary = NotificationSummary(count = 0, text = "")
            listeners.clear()
        }
    }
}
