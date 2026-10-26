package com.vincentwill.calm.classify

/** What the classifier sees about one message. Never contains phone numbers or contact details. */
data class ClassificationInput(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String,
    val conversationTitle: String?,
    val sender: String?,
    val isGroup: Boolean,
    val category: String?,
    val isDialer: Boolean = false,
)

enum class DecisionSource { RULE, AI, FALLBACK }

/** Which step of the evaluation order made the decision. */
enum class DecisionStep(val source: DecisionSource) {
    ALWAYS_URGENT(DecisionSource.RULE),
    CUSTOM_RULE(DecisionSource.RULE),
    ALWAYS_DIGEST(DecisionSource.RULE),
    AI(DecisionSource.AI),
    FALLBACK(DecisionSource.FALLBACK),
}

data class Decision(val urgent: Boolean, val reason: String, val step: DecisionStep) {
    val source: DecisionSource get() = step.source
}

enum class RuleKind { VIP, CUSTOM, DIGEST_APP, MUTED_GROUP }
enum class MatchType { APP, CONVERSATION, SENDER, KEYWORD }
enum class RuleAction { URGENT, DIGEST }

/**
 * A user rule. [packageName] scopes CONVERSATION/SENDER/KEYWORD rules to one app (null = any app);
 * for APP rules [pattern] is the package name.
 */
data class Rule(
    val id: Long = 0,
    val kind: RuleKind,
    val matchType: MatchType,
    val pattern: String,
    val packageName: String? = null,
    val action: RuleAction,
)
