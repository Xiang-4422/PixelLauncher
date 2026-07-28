package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsDraftStoreTest {

    @Test
    fun restoreReturnsEmptyForUnknownConversation() {
        assertEquals("", SmsDraftStore().restore("person:10086", prefilled = ""))
    }

    @Test
    fun updateThenRestoreRoundTripsPerConversation() {
        val store = SmsDraftStore()
        store.update("person:a", "hello")
        store.update("person:b", "world")

        assertEquals("hello", store.restore("person:a", prefilled = ""))
        assertEquals("world", store.restore("person:b", prefilled = ""))
    }

    @Test
    fun prefilledTakesPrecedenceOverStoredDraft() {
        val store = SmsDraftStore()
        store.update("person:a", "stored")

        assertEquals("deep link", store.restore("person:a", prefilled = "deep link"))
    }

    @Test
    fun blankDraftClearsEntry() {
        val store = SmsDraftStore()
        store.update("person:a", "hello")
        store.update("person:a", "  ")

        assertEquals("", store.restore("person:a", prefilled = ""))
    }

    @Test
    fun clearRemovesDraft() {
        val store = SmsDraftStore()
        store.update("person:a", "hello")
        store.clear("person:a")

        assertEquals("", store.restore("person:a", prefilled = ""))
    }

    @Test
    fun blankConversationKeyIsIgnored() {
        val store = SmsDraftStore()
        store.update("", "hello")

        assertEquals("", store.restore("", prefilled = ""))
    }
}
