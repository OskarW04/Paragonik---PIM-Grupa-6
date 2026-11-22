package com.example.paragonik.ocr

import com.example.paragonik.logic.Money
import com.example.paragonik.model.Receipt
import com.example.paragonik.model.ReceiptItem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Linia z OCR z ramką w pikselach; angle to nachylenie linii w radianach.
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val angle: Double = 0.0,
)

// Parser pisany pod paragony z Biedronki (inne sklepy mają zwykle podobny układ).
// Najpierw groupIntoRows składa linie z OCR w wiersze, potem parseRows wyciąga z nich dane regexami.
object ReceiptParser {

    fun parse(lines: List<OcrLine>): Receipt = parseRows(groupIntoRows(lines))

    private class Placed(val line: OcrLine, val x: Double, val y: Double, val height: Double)

    // ML Kit zwykle oddaje osobno kolumnę nazw i kolumnę cen, więc sklejamy linie leżące na tej samej
    // wysokości. Środki linii obracamy o medianę kątów, żeby wyprostować przekrzywione zdjęcie.
    fun groupIntoRows(lines: List<OcrLine>): List<String> {
        if (lines.isEmpty()) return emptyList()

        val angle = lines.map { it.angle }.sorted()[lines.size / 2]
        val cosA = cos(angle)
        val sinA = sin(angle)
        val placed = lines.map { line ->
            val cx = (line.left + line.right) / 2.0
            val cy = (line.top + line.bottom) / 2.0
            Placed(
                line = line,
                x = cx * cosA + cy * sinA,
                y = cy * cosA - cx * sinA,
                height = (line.bottom - line.top).toDouble().coerceAtLeast(1.0),
            )
        }.sortedBy { it.y }

        val rows = mutableListOf<MutableList<Placed>>()
        for (p in placed) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowY = row.map { it.y }.average()
                val rowHeight = row.map { it.height }.average()
                // ten sam wiersz, jeśli środki są bliżej niż pół wysokości tekstu
                if (abs(p.y - rowY) <= 0.5 * min(p.height, rowHeight)) {
                    row.add(p)
                    continue
                }
            }
            rows.add(mutableListOf(p))
        }

        return rows.map { row -> row.sortedBy { it.x }.joinToString(" ") { it.line.text.trim() } }
    }

    private const val MONEY = """\d+[.,]\d{2}"""        // 3,49
    private const val QTY = """\d+(?:[.,]\d{1,3})?"""   // 2 albo 1,965
    private const val TIMES = """\s*[xX×*]\s*"""
    private const val TAIL = """\s*\S{0,2}\s*$"""         // litera stawki PTU na końcu, np. 6,98C

    // "Mleko UHT 3,2 1l C 4 x3,79 15,16C"
    private val ITEM = Regex("""^(.+?)\s+(?:[A-Ga-g]\s*)?($QTY)$TIMES($MONEY)\s+($MONEY)$TAIL""")

    // same liczby, gdy nazwa wylądowała w innym wierszu: "C 1 x2,99 2,99C"
    private val NUMBERS = Regex("""^(?:[A-Ga-g]\s*)?($QTY)$TIMES($MONEY)\s+($MONEY)$TAIL""")

    // OCR potrafi wstawić spację w kwotę: "6, 98", "3 ,965"
    private val SPLIT_DECIMAL = Regex("""(\d) ?([.,]) ?(\d{2,3})(?!\d)""")

    // jedynka przeczytana jako litera przed "x", np. "tx3,97"
    private val MISREAD_ONE = Regex("""(?<=^|\s)[tlIi|](?=\s*[xX×*]\s*\d)""")

    // "0PUST" to OPUST z zerem zamiast O
    private val DISCOUNT_LABEL = Regex("""^(?:[O0]PUST|RABAT|UPUST)\b""")

    // "OPUST -1,00C", "RABAT -0,50"
    private val DISCOUNT = Regex("""^(?:[O0]PUST|RABAT|UPUST)\b.*?[-–—]?\s?($MONEY)""")

    // sama ujemna kwota, gdy napis "OPUST" trafił do innego wiersza
    private val NEGATIVE = Regex("""^[-–—]\s?($MONEY)$TAIL""")

    // "SUMA PLN 69,69" albo "SUMA 12,36", ale nie "SUMA PTU"
    private val TOTAL = Regex("""^SUMA(?:\s*PLN)?\s*:?\s*($MONEY)""")

    // "SUMA PLN" bez kwoty, kwota jest w następnym wierszu
    private val TOTAL_LABEL = Regex("""^SUMA(?:\s*PLN)?\s*:?$""")

    // płatność, na wypadek gdyby nie było sumy: "GOTOWKA 12,36", "KARTA ... 69,69 PLN"
    private val PAYMENT = Regex("""^(?:GOTOWKA|KARTA|PLATNOSC).*?($MONEY)\s*(?:PLN)?$""")

    private val QUANTITY = Regex("""^(\d{1,4})(?:,(\d{1,3}))?$""") // max 9999 sztuk, żeby mnożenie nie przepełniło Long
    private val ENDS_WITH_MONEY = Regex("""$MONEY$TAIL""")
    private val DATE_ISO = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")          // 2024-12-07
    private val DATE_PL = Regex("""\b(\d{2})[.\-/](\d{2})[.\-/](20\d{2})\b""")  // 07.12.2024
    private val CITY = Regex("""\b\d{2}-\d{3}\s+(\p{Lu}[\p{Lu}\s-]*?)(?:\s+(?:UL|AL|PL|OS)\b|,|$)""")

    // pierwszy wiersz stopki kończy listę pozycji
    private val FOOTER = listOf("SPRZED", "SUMA", "PTU", "OPUSTY", "0PUSTY", "LACZNA", "RAZEM", "DO ZAPLATY")

    private val STORES = listOf(
        "BIEDRONKA" to "Biedronka", "LIDL" to "Lidl", "ZABKA" to "Żabka", "KAUFLAND" to "Kaufland",
        "AUCHAN" to "Auchan", "CARREFOUR" to "Carrefour", "NETTO" to "Netto", "DINO" to "Dino",
        "STOKROTKA" to "Stokrotka", "ROSSMANN" to "Rossmann", "LEWIATAN" to "Lewiatan", "ALDI" to "Aldi",
    )

    fun parseRows(rows: List<String>): Receipt {
        val text = rows
            .map { SPLIT_DECIMAL.replace(it.replace(Regex("""\s+"""), " ").trim(), "$1$2$3") }
            .filter { it.isNotEmpty() }
        val upper = text.map { normalize(it) }

        // pozycje zaczynają się pod "PARAGON FISKALNY" (jeśli go znaleźliśmy)
        val header = upper.indexOfFirst { it.contains("PARAGON") && it.contains("FISKALN") }
        val total = findTotal(upper)
        val items = readItems(text, upper, from = if (header >= 0) header + 1 else 0)

        return Receipt(
            store = findStore(text, upper, headerEnd = if (header >= 0) header else min(8, text.size)),
            date = findDate(text),
            total = total,
            items = correctValues(items, total).map { fixQuantityOrUnitPrice(it) },
        )
    }

    // Nazwa i liczby mogą trafić do sąsiednich wierszy, wtedy jedno czeka na drugie.
    private fun readItems(text: List<String>, upper: List<String>, from: Int): List<ReceiptItem> {
        val items = mutableListOf<ReceiptItem>()
        var pendingName: String? = null
        var pendingNumbers: MatchResult? = null

        for (i in from until text.size) {
            val row = MISREAD_ONE.replace(text[i], "1")
            val up = upper[i]
            if (isFooter(up)) break

            // ujemna kwota tuż nad "OPUSTY ŁĄCZNIE" to suma rabatów, a nie rabat pozycji
            val negative = NEGATIVE.matchEntire(row)?.takeUnless { upper.getOrNull(i + 1)?.startsWith("OPUSTY") == true }
            val discount = DISCOUNT.find(up)?.groupValues?.get(1) ?: negative?.groupValues?.get(1)
            // "nazwa" z mniej niż 2 literami to sama stawka PTU
            val item = ITEM.matchEntire(row)?.takeIf { match -> match.groupValues[1].count { it.isLetter() } >= 2 }
            val numbers = NUMBERS.matchEntire(row)
            when {
                // rabat odejmujemy od poprzedniej pozycji
                discount != null -> if (items.isNotEmpty()) {
                    val amount = -abs(Money.parse(discount) ?: 0L)
                    val last = items.removeAt(items.lastIndex)
                    items.add(last.copy(price = last.price + amount, discount = last.discount + amount))
                }
                item != null -> {
                    val (name, qty, unit, value) = item.destructured
                    items.add(newItem(name, qty, unit, value))
                    pendingName = null
                    pendingNumbers = null
                }
                numbers != null -> {
                    val name = pendingName
                    if (name != null) {
                        val (qty, unit, value) = numbers.destructured
                        items.add(newItem(name, qty, unit, value))
                        pendingName = null
                    } else {
                        pendingNumbers = numbers
                    }
                }
                looksLikeName(row, up) -> {
                    val nums = pendingNumbers
                    if (nums != null) {
                        val (qty, unit, value) = nums.destructured
                        items.add(newItem(row, qty, unit, value))
                        pendingNumbers = null
                    } else {
                        pendingName = row
                    }
                }
                // resztę wierszy (np. wartość po rabacie) pomijamy
            }
        }
        return items.mapIndexed { index, item -> item.copy(id = index + 1L) }
    }

    private fun newItem(rawName: String, qty: String, unitPrice: String, value: String) = ReceiptItem(
        id = 0,
        name = cleanName(rawName),
        quantity = qty.replace(" ", "").replace('.', ','),
        unitPrice = Money.parse(unitPrice) ?: 0L,
        price = Money.parse(value) ?: 0L,
    )

    // Jeśli suma pozycji nie zgadza się z SUMA, podmieniamy źle odczytane wartości na ilość × cena,
    // ale tylko gdy to przybliża nas do sumy z paragonu.
    private fun correctValues(items: List<ReceiptItem>, total: Long?): List<ReceiptItem> {
        if (total == null) return items
        val result = items.toMutableList()
        var sum = result.sumOf { it.price }
        for (index in result.indices) {
            if (sum == total) break
            val item = result[index]
            val expected = expectedPrice(item) ?: continue
            val newSum = sum - item.price + expected
            if (abs(total - newSum) < abs(total - sum)) {
                result[index] = item.copy(price = expected)
                sum = newSum
            }
        }
        return result
    }

    // ilość × cena + rabat, w groszach; ilość liczymy w tysięcznych, bo bywa np. 1,965 kg
    private fun expectedPrice(item: ReceiptItem): Long? {
        val match = QUANTITY.matchEntire(item.quantity.trim().replace('.', ',')) ?: return null
        val thousandths = match.groupValues[1].toLong() * 1000 + match.groupValues[2].padEnd(3, '0').toLong()
        if (thousandths == 0L || item.unitPrice <= 0L) return null
        return (thousandths * item.unitPrice + 500) / 1000 + item.discount
    }

    // Czy ilość × cena daje wartość pozycji. Jak nie da się tego policzyć (np. brak ceny), to uznajemy, że tak.
    fun isConsistent(item: ReceiptItem): Boolean {
        val expected = expectedPrice(item) ?: return true
        return expected == item.price
    }

    // OCR myli też cyfry w ilości i cenie jednostkowej. Wartości pozycji nie ruszamy, bo pilnuje jej
    // suma paragonu. Zamiast tego zmieniamy po jednej cyfrze w ilości i w cenie, np. "3,965" -> "1,965",
    // i jeśli dokładnie jeden wariant daje tę wartość, to go bierzemy.
    private fun fixQuantityOrUnitPrice(item: ReceiptItem): ReceiptItem {
        val expected = expectedPrice(item) ?: return item
        if (expected == item.price) return item
        val candidates = oneDigitChanges(item.quantity).map { item.copy(quantity = it) } +
            oneDigitChanges(Money.formatPlain(item.unitPrice)).mapNotNull { Money.parse(it) }.map { item.copy(unitPrice = it) }
        val fixed = candidates.filter { expectedPrice(it) == item.price }
        return when {
            fixed.size == 1 -> fixed.single()
            // przy jednej sztuce cena jednostkowa to po prostu wartość
            fixed.isEmpty() && item.quantity == "1" && item.price - item.discount > 0 ->
                item.copy(unitPrice = item.price - item.discount)
            // kilka możliwości albo żadna: nie zgadujemy, pozycja zostanie oznaczona do sprawdzenia
            else -> item
        }
    }

    // wszystkie wersje tekstu z jedną cyfrą zamienioną na inną: "3,965" -> "0,965", "1,965", ..., "3,964"
    private fun oneDigitChanges(text: String): List<String> =
        text.indices.filter { text[it].isDigit() }.flatMap { i ->
            ('0'..'9').filter { it != text[i] }.map { digit -> text.substring(0, i) + digit + text.substring(i + 1) }
        }

    // "KAWA TCHIBO FAM.25 A" -> "KAWA TCHIBO FAM.25"
    private fun cleanName(name: String) = name.trim().replace(Regex("""\s+[A-Ga-g(|\[]$"""), "").trim()

    private fun looksLikeName(row: String, up: String) =
        row.count { it.isLetter() } >= 3 &&
            !DISCOUNT_LABEL.containsMatchIn(up) &&
            !ENDS_WITH_MONEY.containsMatchIn(row)

    private fun isFooter(up: String) = FOOTER.any { up.startsWith(it) } || up.contains("PTU")

    // SUMA, a jak jej nie ma, to kwota płatności
    private fun findTotal(upper: List<String>): Long? {
        for ((i, row) in upper.withIndex()) {
            TOTAL.find(row)?.let { return Money.parse(it.groupValues[1]) }
            if (TOTAL_LABEL.matches(row)) {
                val next = upper.getOrNull(i + 1)?.let { Money.parse(it) }
                if (next != null) return next
            }
        }
        for (row in upper) {
            PAYMENT.find(row)?.let { return Money.parse(it.groupValues[1]) }
        }
        return null
    }

    // pierwsza sensowna data, zawsze zwracana jako RRRR-MM-DD
    private fun findDate(text: List<String>): String {
        for (row in text) {
            DATE_ISO.find(row)?.let { match ->
                val (year, month, day) = match.destructured
                if (isValidDate(month, day)) return "$year-$month-$day"
            }
            DATE_PL.find(row)?.let { match ->
                val (day, month, year) = match.destructured
                if (isValidDate(month, day)) return "$year-$month-$day"
            }
        }
        return ""
    }

    private fun isValidDate(month: String, day: String) = month.toInt() in 1..12 && day.toInt() in 1..31

    // np. "Biedronka, Iława". Miasto bierzemy z adresu sklepu, który jest nad nazwą spółki
    // (pod nią jest jeszcze adres siedziby firmy, ten pomijamy).
    private fun findStore(text: List<String>, upper: List<String>, headerEnd: Int): String {
        val head = upper.take(headerEnd)
        val chain = STORES.firstOrNull { (key, _) -> head.any { containsSimilarWord(it, key) } }?.second
        val company = head.indexOfFirst { isCompanyLine(it) }
        val city = text.take(if (company >= 0) company else headerEnd).firstNotNullOfOrNull { row ->
            CITY.find(row.uppercase())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        }?.let { titleCase(it) }
        return when {
            chain != null && city != null -> "$chain, $city"
            chain != null -> chain
            else -> text.firstOrNull()?.take(40).orEmpty()
        }
    }

    private fun isCompanyLine(up: String) =
        up.contains("S.A") || up.contains("SP. Z") || up.contains("SP.Z") || up.contains("SPOLKA")

    // OCR myli litery (np. "BIEDROHKA"), więc dopuszczamy kilka błędów, tym więcej, im dłuższe słowo.
    private fun containsSimilarWord(row: String, key: String): Boolean {
        val tolerance = when {
            key.length >= 8 -> 2
            key.length >= 5 -> 1
            else -> 0
        }
        return row.split(Regex("""[^A-Z0-9]+""")).any { word ->
            abs(word.length - key.length) <= tolerance && levenshtein(word, key) <= tolerance
        }
    }

    // ile liter trzeba zmienić, dodać albo usunąć, żeby z a zrobić b
    private fun levenshtein(a: String, b: String): Int {
        val row = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var diagonal = row[0]
            row[0] = i
            for (j in 1..b.length) {
                val above = row[j]
                row[j] = minOf(row[j] + 1, row[j - 1] + 1, diagonal + if (a[i - 1] == b[j - 1]) 0 else 1)
                diagonal = above
            }
        }
        return row[b.length]
    }

    // "OSTRÓW LUBELSKI" -> "Ostrów Lubelski"
    private fun titleCase(s: String) = s.lowercase().split(" ").joinToString(" ") { word ->
        word.split("-").joinToString("-") { part -> part.replaceFirstChar { it.uppercase() } }
    }

    // wielkie litery bez polskich znaków, żeby łatwiej porównywać
    private fun normalize(s: String): String {
        val plain = mapOf(
            'Ą' to 'A', 'Ć' to 'C', 'Ę' to 'E', 'Ł' to 'L', 'Ń' to 'N',
            'Ó' to 'O', 'Ś' to 'S', 'Ź' to 'Z', 'Ż' to 'Z',
        )
        return s.uppercase().map { plain[it] ?: it }.joinToString("")
    }
}
