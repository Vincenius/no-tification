package com.vincentwill.calm.classify

enum class Feedback { SHOULD_BE_URGENT, SHOULD_NOT_BE_URGENT }

/** The parts of a stored decision that feedback needs. */
data class FeedbackEntry(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val sender: String?,
    val conversationTitle: String?,
    val isGroup: Boolean,
    val urgent: Boolean,
    val step: DecisionStep,
    val reason: String,
)

/** One way to fix a wrong decision: add a rule, remove the rule that caused it, or both. */
data class Suggestion(
    val id: String,
    val label: String,
    val detail: String,
    val addRule: Rule? = null,
    val removeRuleId: Long? = null,
)

data class FeedbackOptions(
    val feedback: Feedback,
    val suggestions: List<Suggestion>,
    /** Shown when no rule can change the outcome (calls, alarms, codes). */
    val note: String? = null,
)

/** Turns "this was wrong" into concrete rule suggestions for the entry's sender, conversation or app. */
object FeedbackSuggestions {
    fun forEntry(e: FeedbackEntry, rules: List<Rule>): FeedbackOptions =
        if (e.urgent) shouldNotBeUrgent(e, rules) else shouldBeUrgent(e, rules)

    /** Non-messaging notifications (SMS, mail) carry the sender in the title. */
    private fun who(e: FeedbackEntry): String? = e.sender ?: if (!e.isGroup) e.title else null

    private fun conversation(e: FeedbackEntry): String? = e.conversationTitle?.takeIf { it != who(e) }

    private fun shouldBeUrgent(e: FeedbackEntry, rules: List<Rule>): FeedbackOptions {
        val who = who(e)
        val conversation = conversation(e)
        val suggestions = buildList {
            // Undo whatever made it a digest item.
            rules.firstOrNull { it.kind == RuleKind.DIGEST_APP && it.pattern == e.packageName }?.let {
                add(Suggestion("remove-digest-app", "Stop always holding ${e.appLabel}", "Removes ${e.appLabel} from the hold list", removeRuleId = it.id))
            }
            if (e.isGroup && conversation != null) {
                rules.firstOrNull { it.kind == RuleKind.MUTED_GROUP && it.packageName == e.packageName && same(it.pattern, conversation) }?.let {
                    add(Suggestion("unmute-group", "Unmute $conversation", "Removes it from the muted groups", removeRuleId = it.id))
                }
            }
            if (e.step == DecisionStep.CUSTOM_RULE) {
                causingRule(e, rules)?.let { add(Suggestion("remove-rule", "Remove rule: ${RulesEngine.describe(it)}", "The rule that held it", removeRuleId = it.id)) }
            }
            if (who != null) {
                add(Suggestion("vip", "Always let $who through", "VIP in every app", addRule = Rule(kind = RuleKind.VIP, matchType = MatchType.SENDER, pattern = who, action = RuleAction.URGENT)))
                add(Suggestion("sender-urgent", "Let $who through in ${e.appLabel}", "Only in this app", addRule = custom(MatchType.SENDER, who, e.packageName, RuleAction.URGENT)))
            }
            if (conversation != null) {
                add(Suggestion("conversation-urgent", "Always let \"$conversation\" through", "Every message in this ${if (e.isGroup) "group" else "conversation"}", addRule = custom(MatchType.CONVERSATION, conversation, e.packageName, RuleAction.URGENT)))
            }
            add(Suggestion("app-urgent", "Always let ${e.appLabel} through", "Every notification from this app", addRule = custom(MatchType.APP, e.packageName, null, RuleAction.URGENT)))
        }
        return FeedbackOptions(Feedback.SHOULD_BE_URGENT, suggestions.withoutExisting(rules))
    }

    private fun shouldNotBeUrgent(e: FeedbackEntry, rules: List<Rule>): FeedbackOptions {
        val who = who(e)
        val conversation = conversation(e)
        val vipRule = rules.firstOrNull { it.kind == RuleKind.VIP && e.step == DecisionStep.ALWAYS_URGENT && e.reason == "VIP: ${it.pattern}" }
        if (e.step == DecisionStep.ALWAYS_URGENT && vipRule == null) {
            return FeedbackOptions(
                Feedback.SHOULD_NOT_BE_URGENT,
                emptyList(),
                note = "Calls, alarms, calendar reminders and verification codes always come through, so no rule can hold them.",
            )
        }
        val suggestions = buildList {
            vipRule?.let { add(Suggestion("remove-vip", "Remove ${it.pattern} from VIPs", "They are then treated like everyone else", removeRuleId = it.id)) }
            if (e.step == DecisionStep.CUSTOM_RULE) {
                causingRule(e, rules)?.let { add(Suggestion("remove-rule", "Remove rule: ${RulesEngine.describe(it)}", "The rule that let it through", removeRuleId = it.id)) }
            }
            if (e.isGroup && conversation != null) {
                add(Suggestion("mute-group", "Always hold \"$conversation\"", "Mute this group", addRule = Rule(kind = RuleKind.MUTED_GROUP, matchType = MatchType.CONVERSATION, pattern = conversation, packageName = e.packageName, action = RuleAction.DIGEST)))
            } else if (conversation != null) {
                add(Suggestion("conversation-digest", "Always hold \"$conversation\"", "Every message in this conversation", addRule = custom(MatchType.CONVERSATION, conversation, e.packageName, RuleAction.DIGEST)))
            }
            if (who != null && vipRule == null) {
                add(Suggestion("sender-digest", "Hold messages from $who in ${e.appLabel}", "Only in this app", addRule = custom(MatchType.SENDER, who, e.packageName, RuleAction.DIGEST)))
            }
            add(Suggestion("digest-app", "Always hold ${e.appLabel}", "Calls, codes and VIPs still come through", addRule = Rule(kind = RuleKind.DIGEST_APP, matchType = MatchType.APP, pattern = e.packageName, action = RuleAction.DIGEST)))
        }
        return FeedbackOptions(Feedback.SHOULD_NOT_BE_URGENT, suggestions.withoutExisting(rules))
    }

    /** The custom rule whose description is in the decision reason ("Rule: sender "Anna" → urgent"). */
    private fun causingRule(e: FeedbackEntry, rules: List<Rule>): Rule? =
        rules.firstOrNull { it.kind == RuleKind.CUSTOM && e.reason == "Rule: ${RulesEngine.describe(it)}" }

    private fun custom(type: MatchType, pattern: String, packageName: String?, action: RuleAction) =
        Rule(kind = RuleKind.CUSTOM, matchType = type, pattern = pattern, packageName = packageName, action = action)

    private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    private fun List<Suggestion>.withoutExisting(rules: List<Rule>) = filter { s ->
        val r = s.addRule ?: return@filter true
        rules.none { it.kind == r.kind && it.matchType == r.matchType && it.packageName == r.packageName && it.action == r.action && same(it.pattern, r.pattern) }
    }
}
