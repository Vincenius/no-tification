package com.vincentwill.calm.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSuggestionsTest {
    private fun entry(
        urgent: Boolean,
        step: DecisionStep = DecisionStep.AI,
        reason: String = "AI",
        sender: String? = "Anna",
        conversation: String? = "Anna",
        group: Boolean = false,
        title: String? = "Anna",
    ) = FeedbackEntry("com.whatsapp", "WhatsApp", title, sender, conversation, group, urgent, step, reason)

    private fun FeedbackOptions.ids() = suggestions.map { it.id }

    @Test
    fun `held 1-1 chat suggests vip, sender in app and whole app`() {
        val o = FeedbackSuggestions.forEntry(entry(urgent = false), emptyList())
        assertEquals(Feedback.SHOULD_BE_URGENT, o.feedback)
        // The conversation is just the sender's name, so no separate conversation suggestion.
        assertEquals(listOf("vip", "sender-urgent", "app-urgent"), o.ids())
        val vip = o.suggestions.first().addRule!!
        assertEquals(RuleKind.VIP, vip.kind)
        assertEquals("Anna", vip.pattern)
        val sender = o.suggestions[1].addRule!!
        assertEquals("com.whatsapp", sender.packageName)
        assertEquals(RuleAction.URGENT, sender.action)
    }

    @Test
    fun `held group message offers the group and the sender`() {
        val o = FeedbackSuggestions.forEntry(entry(urgent = false, sender = "Mama", conversation = "Familie", group = true, title = "Familie"), emptyList())
        assertEquals(listOf("vip", "sender-urgent", "conversation-urgent", "app-urgent"), o.ids())
        assertEquals("Familie", o.suggestions[2].addRule!!.pattern)
        assertEquals(MatchType.CONVERSATION, o.suggestions[2].addRule!!.matchType)
    }

    @Test
    fun `held because of the hold list or a muted group offers undoing it`() {
        val rules = listOf(
            Rule(id = 7, kind = RuleKind.DIGEST_APP, matchType = MatchType.APP, pattern = "com.whatsapp", action = RuleAction.DIGEST),
            Rule(id = 8, kind = RuleKind.MUTED_GROUP, matchType = MatchType.CONVERSATION, pattern = "familie", packageName = "com.whatsapp", action = RuleAction.DIGEST),
        )
        val o = FeedbackSuggestions.forEntry(entry(urgent = false, step = DecisionStep.ALWAYS_DIGEST, sender = "Mama", conversation = "Familie", group = true), rules)
        assertEquals(7L, o.suggestions.first { it.id == "remove-digest-app" }.removeRuleId)
        assertEquals(8L, o.suggestions.first { it.id == "unmute-group" }.removeRuleId)
    }

    @Test
    fun `held by a custom rule offers removing exactly that rule`() {
        val rule = Rule(id = 3, kind = RuleKind.CUSTOM, matchType = MatchType.SENDER, pattern = "Anna", packageName = "com.whatsapp", action = RuleAction.DIGEST)
        val o = FeedbackSuggestions.forEntry(entry(urgent = false, step = DecisionStep.CUSTOM_RULE, reason = "Rule: ${RulesEngine.describe(rule)}"), listOf(rule))
        assertEquals(3L, o.suggestions.first { it.id == "remove-rule" }.removeRuleId)
    }

    @Test
    fun `existing rules are not suggested again`() {
        val vip = Rule(id = 1, kind = RuleKind.VIP, matchType = MatchType.SENDER, pattern = "anna", action = RuleAction.URGENT)
        val o = FeedbackSuggestions.forEntry(entry(urgent = false), listOf(vip))
        assertFalse(o.ids().contains("vip"))
    }

    @Test
    fun `sms without sender uses the title`() {
        val o = FeedbackSuggestions.forEntry(entry(urgent = false, sender = null, conversation = null, title = "Mom"), emptyList())
        assertEquals("Mom", o.suggestions.first { it.id == "vip" }.addRule!!.pattern)
    }

    @Test
    fun `urgent group message offers muting the group, holding the sender and the app`() {
        val o = FeedbackSuggestions.forEntry(entry(urgent = true, sender = "Bob", conversation = "Nachbarn", group = true, title = "Nachbarn"), emptyList())
        assertEquals(Feedback.SHOULD_NOT_BE_URGENT, o.feedback)
        assertEquals(listOf("mute-group", "sender-digest", "digest-app"), o.ids())
        assertEquals(RuleKind.MUTED_GROUP, o.suggestions[0].addRule!!.kind)
        assertEquals(RuleKind.DIGEST_APP, o.suggestions[2].addRule!!.kind)
    }

    @Test
    fun `calls and codes can't be overridden`() {
        val o = FeedbackSuggestions.forEntry(entry(urgent = true, step = DecisionStep.ALWAYS_URGENT, reason = "Looks like a verification code"), emptyList())
        assertTrue(o.suggestions.isEmpty())
        assertNotNull(o.note)
    }

    @Test
    fun `urgent because of a vip offers removing the vip`() {
        val vip = Rule(id = 5, kind = RuleKind.VIP, matchType = MatchType.SENDER, pattern = "Anna", action = RuleAction.URGENT)
        val o = FeedbackSuggestions.forEntry(entry(urgent = true, step = DecisionStep.ALWAYS_URGENT, reason = "VIP: Anna"), listOf(vip))
        assertNull(o.note)
        assertEquals("remove-vip", o.suggestions.first().id)
        assertEquals(5L, o.suggestions.first().removeRuleId)
        // Holding Anna by rule wouldn't work while she is a VIP, so it isn't offered.
        assertFalse(o.ids().contains("sender-digest"))
    }

    @Test
    fun `fallback urgent can be fixed by rules`() {
        val o = FeedbackSuggestions.forEntry(entry(urgent = true, step = DecisionStep.FALLBACK, reason = "AI timed out, letting it through"), emptyList())
        assertEquals(listOf("sender-digest", "digest-app"), o.ids())
    }
}
