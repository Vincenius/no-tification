package com.vincentwill.calm.classify

/** Detects one-time passwords / 2FA codes in German and English texts: a digit code plus a keyword. */
object OtpDetector {
    /** Matched as substrings, so compounds like "Instagram-Code" or "Bestätigungscode" hit. */
    private val keywords = listOf(
        "code", "verification", "verify", "passcode", "one-time", "one time", "two-factor",
        "authentication", "login", "sign in", "sign-in",
        "bestätigung", "verifizierung", "einmalpasswort", "einmalkennwort", "authentifizierung",
        "anmeldung", "anmelde", "zugangs", "sicherheits", "freigabe", "kennwort",
    )

    /** Too short for substring matching ("pin" in "shopping"), so they need word boundaries. */
    private val shortKeywords = Regex("""(?i)(?<![\p{L}\p{N}])(otp|2fa|pin|tan|mtan|pushtan|sms-tan)(?![\p{L}\p{N}])""")

    /**
     * 4–8 digits, or 3+3 / 4+4 digits split by a space or dash. Not part of a longer number, a
     * phone number ("+49 151 23456789") or a price ("1234,50").
     */
    private val code = Regex("""(?<!\p{N})(?<!\p{N}[ -])(\d{4,8}|\d{3}[ -]\d{3}|\d{4}[ -]\d{4})(?!\p{N}|[ -]\p{N}|[.,]\p{N})""")

    fun containsOtp(text: String): Boolean {
        if (!code.containsMatchIn(text)) return false
        val lower = text.lowercase()
        return keywords.any { lower.contains(it) } || shortKeywords.containsMatchIn(text)
    }
}
