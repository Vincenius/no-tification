package com.vincentwill.calm.ai

/**
 * Removes contact details before anything leaves the phone. Unknown WhatsApp senders show up as
 * "+49 151 2345678", and message texts can contain numbers or addresses too.
 */
object PrivacyFilter {
    /** 7+ digits, optionally with a leading + and spaces, dashes, dots, slashes or parentheses in between. */
    private val phone = Regex("""(?<![\p{L}\p{N}])\+?\(?\d(?:[\s\-./()]*\d){6,}(?![\p{L}\p{N}])""")
    private val email = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")

    /**
     * Dates like 26.10.2026 or 26/10/2026 also have 7+ digits, but matter for judging urgency. Not
     * part of a longer digit run ("0151 12.10.4567" stays a phone number).
     */
    private val date = Regex("""(?<![\p{N}./])(?<!\p{N}[\s\-()])(?:0?[1-9]|[12]\d|3[01])[./](?:0?[1-9]|1[0-2])[./]\d{2,4}(?![\p{N}]|[./]\p{N})""")

    /** Stands in for a date while phone numbers are masked; never matched by [phone]. */
    private const val DATE_MARK = ""
    private val dateMark = Regex.fromLiteral(DATE_MARK)

    fun clean(text: String): String {
        // Dates are set aside first so that "26.10.2026 14:30" isn't read as one long number.
        val dates = ArrayDeque<String>()
        return text.replace(DATE_MARK, "")
            .replace(email, "[email]")
            .replace(date) { dates += it.value; DATE_MARK }
            .replace(phone, "[phone]")
            .replace(dateMark) { dates.removeFirst() }
    }

    /** A name that is only a phone number becomes "unknown contact" rather than "[phone]". */
    fun cleanName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val cleaned = clean(name).trim()
        return if (cleaned == "[phone]" || cleaned == "[email]") "unknown contact" else cleaned
    }
}
