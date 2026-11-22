package com.example.paragonik.logic

import kotlin.math.abs

// Kwoty trzymamy w groszach (Long), a tu zamieniamy je na tekst i z powrotem.
object Money {

    // 1236 -> "12,36 zł"
    fun format(grosze: Long): String = formatPlain(grosze) + " zł"

    // 1236 -> "12,36"
    fun formatPlain(grosze: Long): String {
        val sign = if (grosze < 0) "-" else ""
        val value = abs(grosze)
        return sign + (value / 100) + "," + (value % 100).toString().padStart(2, '0')
    }

    // "12,36", "12.36", "-1", "5 zł" -> grosze; null, jeśli to nie jest kwota
    fun parse(text: String): Long? {
        val cleaned = text.trim()
            .removeSuffix("zł").removeSuffix("PLN")
            .replace(" ", "")
            .replace(',', '.')
        // max 9 cyfr złotych, bo dłuższe liczby mogłyby przepełnić Long
        val match = Regex("""^(-)?(\d{1,9})(?:\.(\d{1,2}))?$""").matchEntire(cleaned) ?: return null
        val (minus, zlote, grosze) = match.destructured
        val value = zlote.toLong() * 100 + grosze.padEnd(2, '0').toLong()
        return if (minus == "-") -value else value
    }
}
