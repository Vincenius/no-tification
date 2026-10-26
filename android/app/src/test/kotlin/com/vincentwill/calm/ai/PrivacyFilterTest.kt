package com.vincentwill.calm.ai

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyFilterTest {
    @Test
    fun `phone numbers in many formats are masked`() {
        listOf(
            "+49 151 23456789",
            "+49 (0) 151 2345678",
            "0151-2345678",
            "0171/9876543",
            "+4915123456789",
            "030 1234567",
        ).forEach { assertEquals(it, "Call [phone] now", PrivacyFilter.clean("Call $it now")) }
    }

    @Test
    fun `emails are masked`() = assertEquals("Mail [email] please", PrivacyFilter.clean("Mail anna.schmidt+x@example.co.uk please"))

    @Test
    fun `short numbers times and dates survive`() {
        listOf("See you at 18:30", "Room 1204", "On 26.10.2026", "Code 4821", "It costs 12,99 €").forEach {
            assertEquals(it, PrivacyFilter.clean(it))
        }
    }

    @Test
    fun `a sender that is only a number becomes unknown contact`() {
        assertEquals("unknown contact", PrivacyFilter.cleanName("+49 151 23456789"))
        assertEquals("Anna", PrivacyFilter.cleanName(" Anna "))
        assertNull(PrivacyFilter.cleanName("  "))
    }

    @Test
    fun `prompt content contains only the allowed fields`() {
        val content = AiPrompt.userContent(
            AiRequest(
                appLabel = "Signal",
                conversationTitle = null,
                isGroup = true,
                messages = listOf("Bob" to "line one\nline two", null to "anonymous"),
                now = ZonedDateTime.of(2026, 10, 31, 9, 5, 0, 0, ZoneId.of("Europe/Berlin")),
            ),
        )
        assertTrue(content, content.startsWith("Current local time: Saturday 2026-10-31 09:05"))
        assertTrue(content.contains("App: Signal"))
        assertTrue(content.contains("Conversation: a group chat"))
        assertTrue(content.contains("- Bob: line one line two"))
        assertTrue(content.contains("- anonymous"))
        assertFalse(content.contains("org.") || content.contains("com."))
    }

    @Test
    fun `long batches keep only the newest messages`() {
        val content = AiPrompt.userContent(
            AiRequest("App", "Chat", false, (1..30).map { "A" to "message $it" }, ZonedDateTime.now()),
        )
        assertFalse(content.contains("message 10\n"))
        assertTrue(content.contains("message 11"))
        assertTrue(content.contains("message 30"))
    }
}
