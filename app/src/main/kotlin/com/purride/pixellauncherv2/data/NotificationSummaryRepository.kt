package com.purride.pixellauncherv2.data

import com.purride.pixellauncherv2.launcher.NotificationSummary
import com.purride.pixellauncherv2.launcher.NotificationSignal
import com.purride.pixellauncherv2.launcher.NotificationSummaryModel
import com.purride.pixellauncherv2.launcher.NotificationSummaryRules

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
    private var signals: List<NotificationSignal> = emptyList()
    private var rules: NotificationSummaryRules = NotificationSummaryRules()
    private val listeners = linkedSetOf<(NotificationSummary) -> Unit>()

    fun current(): NotificationSummary = synchronized(lock) { summary }

    fun update(nextSummary: NotificationSummary) {
        val targets = synchronized(lock) {
            summary = nextSummary
            listeners.toList()
        }
        targets.forEach { listener -> listener(nextSummary) }
    }

    fun updateSignals(
        nextSignals: List<NotificationSignal>,
        nextRules: NotificationSummaryRules,
    ) {
        publish(
            nextSignals = nextSignals,
            nextRules = nextRules,
        )
    }

    fun updateRules(nextRules: NotificationSummaryRules) {
        publish(
            nextSignals = synchronized(lock) { signals },
            nextRules = nextRules,
        )
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
            signals = emptyList()
            rules = NotificationSummaryRules()
            listeners.clear()
        }
    }

    private fun publish(
        nextSignals: List<NotificationSignal>,
        nextRules: NotificationSummaryRules,
    ) {
        val nextSummary = NotificationSummaryModel.summarize(
            signals = nextSignals,
            rules = nextRules,
        )
        val targets = synchronized(lock) {
            signals = nextSignals
            rules = nextRules
            summary = nextSummary
            listeners.toList()
        }
        targets.forEach { listener -> listener(nextSummary) }
    }
}
