package com.vincentwill.calm.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesEngineTest {
    private fun input(
        pkg: String = "com.whatsapp",
        app: String = "WhatsApp",
        title: String? = "Anna",
        text: String = "hey",
        conversation: String? = null,
        sender: String? = "Anna",
        group: Boolean = false,
        category: String? = "msg",
    ) = ClassificationInput(pkg, app, title, text, conversation, sender, group, category)

    private fun vip(name: String) = Rule(kind = RuleKind.VIP, matchType = MatchType.SENDER, pattern = name, action = RuleAction.URGENT)
    private fun custom(type: MatchType, pattern: String, action: RuleAction, pkg: String? = null) =
        Rule(kind = RuleKind.CUSTOM, matchType = type, pattern = pattern, packageName = pkg, action = action)
    private fun digestApp(pkg: String) = Rule(kind = RuleKind.DIGEST_APP, matchType = MatchType.APP, pattern = pkg, action = RuleAction.DIGEST)
    private fun mutedGroup(name: String, pkg: String) =
        Rule(kind = RuleKind.MUTED_GROUP, matchType = MatchType.CONVERSATION, pattern = name, packageName = pkg, action = RuleAction.DIGEST)

    @Test
    fun `unknown message is the gray zone`() = assertNull(RulesEngine.classify(input(), emptyList()))

    @Test
    fun `fallback fails open`() {
        val d = RulesEngine.fallback()
        assertTrue(d.urgent)
        assertEquals(DecisionSource.FALLBACK, d.source)
    }

    @Test
    fun `calls alarms and calendar are always urgent`() {
        listOf(
            input(category = "call"),
            input(category = "missed_call"),
            input(category = "alarm"),
            input(category = "reminder"),
            input(category = "event"),
            input(pkg = "com.google.android.dialer", category = null),
            input(pkg = "com.google.android.deskclock", category = null),
            input(pkg = "com.google.android.calendar", category = null),
            input(pkg = "org.fossify.phone", category = null).copy(isDialer = true),
        ).forEach {
            val d = RulesEngine.classify(it, listOf(digestApp(it.packageName)))!!
            assertTrue(it.toString(), d.urgent)
            assertEquals(DecisionStep.ALWAYS_URGENT, d.step)
        }
    }

    @Test
    fun `otp beats the digest list`() {
        val d = RulesEngine.classify(
            input(pkg = "com.bank", text = "Ihr Bestätigungscode lautet 4821"),
            listOf(digestApp("com.bank")),
        )!!
        assertTrue(d.urgent)
        assertEquals(DecisionStep.ALWAYS_URGENT, d.step)
    }

    @Test
    fun `vip matches sender case and whitespace insensitive`() {
        val d = RulesEngine.classify(input(sender = "  anna   Schmidt "), listOf(vip("Anna Schmidt")))!!
        assertTrue(d.urgent)
        assertEquals(DecisionStep.ALWAYS_URGENT, d.step)
    }

    @Test
    fun `vip matches title for non-messaging notifications`() {
        val d = RulesEngine.classify(input(pkg = "com.google.android.apps.messaging", sender = null, title = "Mom"), listOf(vip("mom")))
        assertTrue(d!!.urgent)
    }

    @Test
    fun `vip in a group chat is urgent but the group name is not a vip`() {
        assertTrue(RulesEngine.classify(input(conversation = "Family", group = true, sender = "Mom"), listOf(vip("Mom")))!!.urgent)
        assertNull(RulesEngine.classify(input(conversation = "Mom", group = true, sender = "Bob", title = "Mom"), listOf(vip("Mom"))))
    }

    @Test
    fun `vip beats custom digest rule`() {
        val d = RulesEngine.classify(input(), listOf(custom(MatchType.APP, "com.whatsapp", RuleAction.DIGEST), vip("Anna")))!!
        assertTrue(d.urgent)
        assertEquals(DecisionStep.ALWAYS_URGENT, d.step)
    }

    @Test
    fun `custom rules by app conversation sender keyword`() {
        assertFalse(RulesEngine.classify(input(), listOf(custom(MatchType.APP, "com.whatsapp", RuleAction.DIGEST)))!!.urgent)
        assertTrue(RulesEngine.classify(input(conversation = "Team", group = true), listOf(custom(MatchType.CONVERSATION, "team", RuleAction.URGENT)))!!.urgent)
        assertFalse(RulesEngine.classify(input(sender = "Newsbot"), listOf(custom(MatchType.SENDER, "Newsbot", RuleAction.DIGEST)))!!.urgent)
        assertTrue(RulesEngine.classify(input(text = "Server DOWN again"), listOf(custom(MatchType.KEYWORD, "down", RuleAction.URGENT)))!!.urgent)
    }

    @Test
    fun `keyword matches whole words only`() {
        assertNull(RulesEngine.classify(input(text = "Countdown started"), listOf(custom(MatchType.KEYWORD, "down", RuleAction.URGENT))))
        assertTrue(RulesEngine.classify(input(text = "Bin gleich da, Übergabe?"), listOf(custom(MatchType.KEYWORD, "übergabe", RuleAction.URGENT)))!!.urgent)
    }

    @Test
    fun `custom rule scoped to another app does not match`() {
        assertNull(RulesEngine.classify(input(sender = "Anna"), listOf(custom(MatchType.SENDER, "Anna", RuleAction.DIGEST, pkg = "org.telegram.messenger"))))
    }

    @Test
    fun `conflicting custom rules prefer urgent`() {
        val d = RulesEngine.classify(
            input(text = "urgent: invoice"),
            listOf(custom(MatchType.KEYWORD, "invoice", RuleAction.DIGEST), custom(MatchType.KEYWORD, "urgent", RuleAction.URGENT)),
        )!!
        assertTrue(d.urgent)
        assertEquals(DecisionStep.CUSTOM_RULE, d.step)
    }

    @Test
    fun `custom urgent rule beats digest list`() {
        val d = RulesEngine.classify(input(), listOf(digestApp("com.whatsapp"), custom(MatchType.SENDER, "Anna", RuleAction.URGENT)))!!
        assertTrue(d.urgent)
    }

    @Test
    fun `digest list muted groups and promo categories`() {
        assertEquals(DecisionStep.ALWAYS_DIGEST, RulesEngine.classify(input(), listOf(digestApp("com.whatsapp")))!!.step)
        assertFalse(RulesEngine.classify(input(conversation = "Nachbarn", group = true), listOf(mutedGroup("Nachbarn", "com.whatsapp")))!!.urgent)
        assertNull(RulesEngine.classify(input(conversation = "Nachbarn", group = true, pkg = "org.telegram.messenger"), listOf(mutedGroup("Nachbarn", "com.whatsapp"))))
        assertFalse(RulesEngine.classify(input(category = "promo"), emptyList())!!.urgent)
        assertFalse(RulesEngine.classify(input(category = "social"), emptyList())!!.urgent)
    }
}
