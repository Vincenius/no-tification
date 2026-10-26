package com.vincentwill.calm.ai

/**
 * Removes contact details before anything leaves the phone. Unknown WhatsApp senders show up as
 * "+49 151 2345678", and message texts can contain numbers or addresses too.
 */
object PrivacyFilter {
    /** 7+ digits, optionally with a leading + and spaces, dashes, dots, slashes or parentheses in between. */
    private val phone = Regex("""(?<![\p{L}\p{N}])\+?\(?\d(?:[\s\-./()]*\d){6,}(?![\p{L}\p{N}])""")
    private val email = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")

    /** Dates like 26.10.2026 or 26/10/2026 also have 7+ digits, but matter for judging urgency. */
    private val date = Regex("""\d{1,2}[./]\d{1,2}[./]\d{2,4}""")

    fun clean(text: String): String = text
        .replace(email, "[email]")
        .replace(phone) { if (date.matches(it.value)) it.value else "[phone]" }

    /** A name that is only a phone number becomes "unknown contact" rather than "[phone]". */
    fun cleanName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val cleaned = clean(name).trim()
        return if (cleaned == "[phone]" || cleaned == "[email]") "unknown contact" else cleaned
    }
}
