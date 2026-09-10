package com.echomind.data.util

object DataRedactor {

    private val EMAIL_REGEX = Regex(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
        RegexOption.IGNORE_CASE
    )

    private val PHONE_REGEX = Regex(
        """\+?\b\d{1,4}[-.\s]?\(?\d{2,4}\)?[-.\s]?\d{3,4}[-.\s]?\d{3,4}\b"""
    )

    private val IP_REGEX = Regex(
        "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    )

    fun redact(text: String): String {
        if (text.isBlank()) return text
        return text
            .replace(EMAIL_REGEX, "[email]")
            .replace(PHONE_REGEX, "[phone]")
            .replace(IP_REGEX, "[ip]")
    }
}
