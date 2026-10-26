package com.vincentwill.calm.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduplicatorTest {
    private fun msg(sender: String?, text: String, ts: Long, self: Boolean = false) = CapturedMessage(sender, text, ts, self)

    private fun notification(key: String = "0|com.whatsapp|1|anna|10123", vararg messages: CapturedMessage, text: String? = null) =
        CapturedNotification(
            id = 1, key = key, packageName = "com.whatsapp", appLabel = "WhatsApp", title = "Anna", text = text,
            conversationTitle = null, isGroupConversation = false, category = "msg", postTime = 1000,
            messages = messages.toList(), hasContentIntent = true,
        )

    @Test
    fun `whatsapp style updates only yield new messages`() {
        val known = mutableSetOf<String>()

        val first = notification(messages = arrayOf(msg("Anna", "Hi", 1)))
        val new1 = Deduplicator.newItems(first) { it in known }
        assertEquals(listOf("Hi"), new1.map { it.text })
        known += new1.map { it.hash }

        // "2 new messages": the old one is repeated.
        val second = notification(messages = arrayOf(msg("Anna", "Hi", 1), msg("Anna", "Are you coming?", 2)))
        val new2 = Deduplicator.newItems(second) { it in known }
        assertEquals(listOf("Are you coming?"), new2.map { it.text })
        known += new2.map { it.hash }

        // Same content reposted (e.g. on read-state sync): nothing new.
        assertTrue(Deduplicator.newItems(second) { it in known }.isEmpty())
    }

    @Test
    fun `same text at a different time is a new message`() {
        val a = Deduplicator.items(notification(messages = arrayOf(msg("Anna", "ok", 1))))
        val b = Deduplicator.items(notification(messages = arrayOf(msg("Anna", "ok", 2))))
        assertNotEquals(a.single().hash, b.single().hash)
    }

    @Test
    fun `same message in another conversation is not a duplicate`() {
        val a = Deduplicator.items(notification(key = "k1", messages = arrayOf(msg("Anna", "Hi", 1))))
        val b = Deduplicator.items(notification(key = "k2", messages = arrayOf(msg("Anna", "Hi", 1))))
        assertNotEquals(a.single().hash, b.single().hash)
    }

    @Test
    fun `own replies are skipped`() {
        val items = Deduplicator.items(notification(messages = arrayOf(msg("Anna", "Dinner?", 1), msg(null, "Yes!", 2, self = true))))
        assertEquals(listOf("Dinner?"), items.map { it.text })
    }

    @Test
    fun `if every message is marked as self they are kept`() {
        val items = Deduplicator.items(notification(messages = arrayOf(msg(null, "Hi", 1, self = true))))
        assertEquals(1, items.size)
    }

    @Test
    fun `duplicates within one notification are collapsed`() {
        val items = Deduplicator.items(notification(messages = arrayOf(msg("Anna", "Hi", 1), msg("Anna", "Hi", 1))))
        assertEquals(1, items.size)
    }

    @Test
    fun `non messaging notifications dedup on title and text`() {
        val a = Deduplicator.items(notification(text = "Your parcel arrives today"))
        val same = Deduplicator.items(notification(text = "Your parcel arrives today"))
        val changed = Deduplicator.items(notification(text = "Your parcel was delivered"))
        assertEquals(a.single().hash, same.single().hash)
        assertNotEquals(a.single().hash, changed.single().hash)
    }
}
