package com.example.paragonik.model

// Wszystkie kwoty są w groszach (Long), żeby nie było błędów zaokrągleń jak przy Double.
data class ReceiptItem(
    val id: Long,
    val name: String,
    val quantity: String = "1", // tekst, bo bywa ułamkowa (towar na wagę)
    val unitPrice: Long = 0,
    val price: Long, // wartość po rabacie, to ją dzielimy między osoby
    val discount: Long = 0, // rabat (<= 0), już odjęty od price
)

data class Person(
    val id: Long,
    val name: String,
    val isMe: Boolean = false,
    val accountId: String? = null, // id konta na serwerze; null = gość
) {
    // "Jan Kowalski" -> "JK", "Ania" -> "AN"
    val initials: String
        get() {
            val words = name.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            return when (words.size) {
                0 -> "?"
                1 -> words[0].take(2).uppercase()
                else -> (words[0].take(1) + words[1].take(1)).uppercase()
            }
        }
}

data class Receipt(
    val store: String = "",
    val date: String = "", // RRRR-MM-DD albo pusty
    val total: Long? = null, // SUMA z paragonu, null jeśli nie odczytano
    val items: List<ReceiptItem> = emptyList(),
)
