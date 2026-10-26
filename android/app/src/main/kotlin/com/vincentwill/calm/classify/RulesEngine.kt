package com.vincentwill.calm.classify

/**
 * Deterministic part of the classification. Steps, in order:
 * 1. always urgent (calls, alarms, calendar, OTP, VIPs)
 * 2. custom rules
 * 3. always digest (digest apps, muted groups, promo/social categories)
 *
 * Returns null for the gray zone, which goes to the AI (or [fallback]).
 */
object RulesEngine {
    private val urgentCategories = setOf("call", "missed_call", "alarm", "reminder", "event")
    private val digestCategories = setOf("promo", "social", "recommendation")

    private val dialerPackages = setOf(
        "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
        "com.android.server.telecom", "com.android.phone",
    )
    private val clockPackages = setOf(
        "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
    )
    private val calendarPackages = setOf(
        "com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar",
    )

    fun classify(input: ClassificationInput, rules: List<Rule>): Decision? =
        alwaysUrgent(input, rules) ?: customRules(input, rules) ?: alwaysDigest(input, rules)

    /** Used when no AI is available or the AI failed: fail open. */
    fun fallback(reason: String = "No rule matched, letting it through") =
        Decision(urgent = true, reason = reason, step = DecisionStep.FALLBACK)

    private fun alwaysUrgent(input: ClassificationInput, rules: List<Rule>): Decision? {
        fun urgent(reason: String) = Decision(true, reason, DecisionStep.ALWAYS_URGENT)
        val pkg = input.packageName
        return when {
            input.isDialer || pkg in dialerPackages -> urgent("Phone call")
            input.category in urgentCategories -> urgent("${input.category!!.replace('_', ' ')} notification")
            pkg in clockPackages -> urgent("Alarm or timer")
            pkg in calendarPackages -> urgent("Calendar reminder")
            OtpDetector.containsOtp(listOfNotNull(input.title, input.text).joinToString(" ")) ->
                urgent("Looks like a verification code")
            else -> rules.firstOrNull { it.kind == RuleKind.VIP && matches(it, input) }
                ?.let { urgent("VIP: ${it.pattern}") }
        }
    }

    /** If both an urgent and a digest rule match, urgent wins (fail open). */
    private fun customRules(input: ClassificationInput, rules: List<Rule>): Decision? {
        val matching = rules.filter { it.kind == RuleKind.CUSTOM && matches(it, input) }
        val rule = matching.firstOrNull { it.action == RuleAction.URGENT } ?: matching.firstOrNull() ?: return null
        return Decision(rule.action == RuleAction.URGENT, "Rule: ${describe(rule)}", DecisionStep.CUSTOM_RULE)
    }

    private fun alwaysDigest(input: ClassificationInput, rules: List<Rule>): Decision? {
        fun digest(reason: String) = Decision(false, reason, DecisionStep.ALWAYS_DIGEST)
        return when {
            rules.any { it.kind == RuleKind.DIGEST_APP && matches(it, input) } ->
                digest("${input.appLabel} is on the digest list")
            rules.any { it.kind == RuleKind.MUTED_GROUP && matches(it, input) } ->
                digest("Muted group")
            input.category in digestCategories -> digest("${input.category} notification")
            else -> null
        }
    }

    fun matches(rule: Rule, input: ClassificationInput): Boolean {
        if (rule.matchType != MatchType.APP && rule.packageName != null && rule.packageName != input.packageName) {
            return false
        }
        return when (rule.matchType) {
            MatchType.APP -> rule.pattern == input.packageName
            MatchType.CONVERSATION -> sameName(rule.pattern, input.conversationTitle)
            // Non-messaging notifications (SMS, mail) usually put the sender in the title.
            MatchType.SENDER -> sameName(rule.pattern, input.sender ?: input.title) ||
                (!input.isGroup && sameName(rule.pattern, input.conversationTitle))
            MatchType.KEYWORD -> containsWord(listOfNotNull(input.title, input.text).joinToString(" "), rule.pattern)
        }
    }

    fun describe(rule: Rule): String = when (rule.matchType) {
        MatchType.APP -> "app ${rule.pattern}"
        MatchType.CONVERSATION -> "conversation \"${rule.pattern}\""
        MatchType.SENDER -> "sender \"${rule.pattern}\""
        MatchType.KEYWORD -> "keyword \"${rule.pattern}\""
    } + if (rule.action == RuleAction.URGENT) " → urgent" else " → digest"

    private fun normalize(name: String) = name.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun sameName(pattern: String, value: String?) =
        value != null && normalize(pattern).isNotEmpty() && normalize(pattern) == normalize(value)

    private fun containsWord(haystack: String, keyword: String): Boolean {
        val k = keyword.trim()
        if (k.isEmpty()) return false
        return Regex("(?iu)(?<![\\p{L}\\p{N}])${Regex.escape(k)}(?![\\p{L}\\p{N}])").containsMatchIn(haystack)
    }
}
