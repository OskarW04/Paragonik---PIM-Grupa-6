package com.example.paragonik

import com.example.paragonik.ocr.OcrLine
import com.example.paragonik.ocr.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun fixesMisreadQuantity() {
        val receipt = ReceiptParser.parseRows(listOf("PARAGON FISKALNY", "MANDARYNKA LUZ T B 3,965x2,49 4,89B", "SUMA 4,89"))
        assertEquals("1,965", receipt.items.single().quantity)
        assertEquals(489L, receipt.items.single().price)
    }

    @Test
    fun fixesMisreadUnitPrice() {
        val receipt = ReceiptParser.parseRows(listOf("PARAGON FISKALNY", "NapGazOrango2l C 2 x3,40 6,98C", "SUMA PLN 6,98"))
        assertEquals(349L, receipt.items.single().unitPrice)
        assertEquals(698L, receipt.items.single().price)
    }

    @Test
    fun marksItemWhenItIsNotClearWhatToFix() {
        // tu pasuje i ilość 3 zamiast 2, i cena 1,50 zamiast 1,00, więc nie zgadujemy
        val receipt = ReceiptParser.parseRows(listOf("PARAGON FISKALNY", "Bulka C 2 x1,00 3,00C", "SUMA 3,00"))
        val item = receipt.items.single()
        assertEquals("2", item.quantity)
        assertEquals(100L, item.unitPrice)
        assertFalse(ReceiptParser.isConsistent(item))
    }

    // dwa prawdziwe paragony przepisane ręcznie ze zdjęć
    private val biedronka2024 = listOf(
        "BIEDRONKA \"CODZIENNIE NISKIE CENY\" 4839",
        "21-110 OSTRÓW LUBELSKI UL. NOWY ŚWIAT 1",
        "JERONIMO MARTINS POLSKA S.A.",
        "62-025 KOSTRZYN UL.ŻNIWNA 5",
        "NIP 7791011327 nr:446045",
        "PARAGON FISKALNY",
        "NapGazOrango2l C 2 x3,49 6,98C",
        "OPUST -1,00C",
        "5,98",
        "ChipsyKettleTruf125g C 1 x4,99 4,99C",
        "ChlebPszenŻyt550g C 1 x2,78 2,78C",
        "PłatkiHopkiVitan500g C 1 x6,99 6,99C",
        "Mleko UHT 3,2 1l C 4 x3,79 15,16C",
        "Hamburger Yano250g C 1 x3,29 3,29C",
        "SerEdamskiŚw 150g C 1 x5,49 5,49C",
        "SerŁMierzwEdams 150g C 1 x5,79 5,79C",
        "Miel Tyrolsk 270g C 1 x5,89 5,89C",
        "PiwoKustMocne0,5l A 1 x2,85 2,85A",
        "Tost Pszenny 500g C 1 x2,99 2,99C",
        "Krem Chocofini400g A 1 x7,49 7,49A",
        "OPUSTY ŁĄCZNIE -1,00",
        "SPRZEDAŻ OPODATKOWANA A 10,34",
        "SPRZEDAŻ OPODATKOWANA C 59,35",
        "PTU A 23% 1,93",
        "PTU C 5% 2,83",
        "SUMA PTU 4,76",
        "SUMA PLN 69,69",
        "ROZLICZENIE PŁATNOŚCI",
        "KARTA DEBIT MASTERCARD 69,69 PLN",
        "00026 #Kasa 3 Kasjer nr 32 2024-12-07 09:24",
    )

    private val biedronka2006 = listOf(
        "BIEDRONKA \"CODZIENNIE NISKIE CENY\" 1334",
        "14-200 IŁAWA ul.Wyszyńskiego 6",
        "JERONIMO MARTINS DYSTRYBUCJA S.A.",
        "62-025 KOSTRZYN UL.ZNIWNA 5",
        "NIP 779-10-11-327",
        "2006-01-09 354757",
        "PARAGON FISKALNY",
        "KAWA TCHIBO FAM.25 A 1x3,97 3,97A",
        "SER TWAROGOWY 250G B 1x1,75 1,75B",
        "SER TWAROGOWY 250G B 1x1,75 1,75B",
        "MANDARYNKA LUZ T B 1,965x2,49 4,89B",
        "Sprzed. opodatk. A 3,97",
        "Kwota PTU A 22 % 0,72",
        "Sprzed. opodatk. B 8,39",
        "Kwota PTU B 7 % 0,55",
        "ŁĄCZNA KWOTA PTU 1,27",
        "SUMA 12,36",
        "Gotówka 12,36",
        "0350 #Kasa 1 Kasjer nr 11 15:12",
    )

    // te same paragony tak, jak je zwrócił ML Kit (z błędami OCR)
    private val ocr2024 = listOf(
        "Biedronka",
        "Codziennie niskie ceny",
        "8IEORONKA \"CODZIENNIE NISKIE CENY\" 4839",
        "21-110 0SIROW LUBELSKI UL. NOWY SMIAT 1",
        "JERONIHO MARTINS POL SKA S.A.",
        "62-025 KOSTRZYN UL.ZNIMWNA 5",
        "NIP 791011327 nr 446045",
        "PARAGON FISKALNY",
        "NapGazdraigo2\\ 2 x3,49 6,98C",
        "OPUST -1,00C",
        "5,98",
        "Chipsukettlelruf1259 1 x4,99 4,99C",
        "ChlebPszenżyt550g 1 x2,76 2,78C",
        "PłatkıHopkiŬitan500g C 1 x6,99 6, 99C",
        "Mleko UHT 3,2 1L 4 x3,79 15, 16C",
        "Hanburger Yano250g 1 x3, 29 3, 29C",
        "SerEdamskiSu 1509 1 x5,49 5,49C",
        "SertMierzuEdans 150g 1 x5, 79 5,79C",
        "Miel Tyrolsk 270g C 1 x5, 89 5, 89C",
        "PiuokustMocne0,5l A 1 x2,85 2,85A",
        "Tost Pszenny 500g 1 x2,99 2,99C",
        "Kren Chocofini400g A 1 x7,49 1,49A",
        "OPUSTY ŁACZNIE 1,00",
        "SPRZEDAZ OPODATKOMANA A 10,34",
        "SPRZEDAZ OPODATKONANA C S9,35",
        "PTU A 23% 1,93",
        "PrU C 5% 2,83",
        "SUMA PTU 4,76",
        "SUMA PLN 69,69",
        "00026 #kasa 3 Kasjer nr 32 2024-12-07 09:24",
    )

    private val ocr2006 = listOf(
        "BIEDROHKA \"CODZIENN IE NISKIE CENY\" 1334",
        "14-200 ILAWA ul .Myszyńskiego 6",
        "JERON IMO MART INS DYSTRY8UCOA S.A.",
        "62-025 KOSTRZYN VL.ZNIMNA 5",
        "NIP 779-18-11-327",
        "2006-01-09 354757",
        "PARAGON FISKALNY",
        "KAWA TCHIB0 FAM.25 A tx3,97 3,97A",
        "SER TMAROGOW) 250G B tx1,75 1,758",
        "SER TWAROGOMY 250G B 1x1,75 1,758",
        "MANDARYNKA LVZ T 3 ,965x2,49 4,898",
        "Sprzed. opodatk. A 3,97",
        "Kuota PTU A 22 % 0,72",
        "Sprzed. opodatk. B 8,39",
        "Kuota PTU' B7% 0,55",
        "kgCZNA KHOTA PTU 1,27",
        "SUMA 12,36",
        "Gotóuka 12,36",
        "0350 Kasa 1 Kasjer nr 11 15:12",
    )

    @Test
    fun readsRealOcrOutputOf2024Receipt() {
        val receipt = ReceiptParser.parseRows(ocr2024)

        assertEquals("Biedronka", receipt.store)
        assertEquals("2024-12-07", receipt.date)
        assertEquals(6969L, receipt.total)
        assertEquals(12, receipt.items.size)
        assertEquals(6969L, receipt.items.sumOf { it.price })
        // OCR przeczytał 7,49 jako 1,49, poprawka z ilość × cena
        assertEquals(749L, receipt.items.last().price)
        // OCR przeczytał cenę jedn. 2,76, a przy 1 sztuce musi być równa wartości 2,78
        assertEquals(278L, receipt.items[2].price)
        assertEquals(278L, receipt.items[2].unitPrice)
        assertTrue(receipt.items.all { ReceiptParser.isConsistent(it) })
    }

    @Test
    fun readsRealOcrOutputOf2006Receipt() {
        val receipt = ReceiptParser.parseRows(ocr2006)

        assertEquals("Biedronka, Ilawa", receipt.store)
        assertEquals("2006-01-09", receipt.date)
        assertEquals(1236L, receipt.total)
        assertEquals(listOf(397L, 175L, 175L, 489L), receipt.items.map { it.price })
        // OCR przeczytał wagę mandarynek jako 3,965, a tylko 1,965 × 2,49 daje 4,89
        assertEquals("1,965", receipt.items[3].quantity)
        assertTrue(receipt.items.all { ReceiptParser.isConsistent(it) })
    }

    @Test
    fun readsBiedronka2024Receipt() {
        val receipt = ReceiptParser.parseRows(biedronka2024)

        assertEquals("Biedronka, Ostrów Lubelski", receipt.store)
        assertEquals("2024-12-07", receipt.date)
        assertEquals(6969L, receipt.total)
        assertEquals(12, receipt.items.size)
        assertEquals(6969L, receipt.items.sumOf { it.price })

        val orangeade = receipt.items[0]
        assertEquals("NapGazOrango2l", orangeade.name)
        assertEquals("2", orangeade.quantity)
        assertEquals(349L, orangeade.unitPrice)
        assertEquals(598L, orangeade.price) // 6,98 minus 1,00 rabatu
        assertEquals(-100L, orangeade.discount)

        val milk = receipt.items[4]
        assertEquals("Mleko UHT 3,2 1l", milk.name)
        assertEquals("4", milk.quantity)
        assertEquals(379L, milk.unitPrice)
        assertEquals(1516L, milk.price)

        assertEquals("Krem Chocofini400g", receipt.items.last().name)
    }

    @Test
    fun readsBiedronka2006Receipt() {
        val receipt = ReceiptParser.parseRows(biedronka2006)

        assertEquals("Biedronka, Iława", receipt.store)
        assertEquals("2006-01-09", receipt.date)
        assertEquals(1236L, receipt.total)
        assertEquals(
            listOf("KAWA TCHIBO FAM.25", "SER TWAROGOWY 250G", "SER TWAROGOWY 250G", "MANDARYNKA LUZ T"),
            receipt.items.map { it.name },
        )
        assertEquals(1236L, receipt.items.sumOf { it.price })

        val tangerines = receipt.items[3]
        assertEquals("1,965", tangerines.quantity) // towar ważony
        assertEquals(249L, tangerines.unitPrice)
        assertEquals(489L, tangerines.price)
    }

    @Test
    fun joinsNameAndPricesFromSeparateRows() {
        val receipt = ReceiptParser.parseRows(
            listOf("PARAGON FISKALNY", "Tost Pszenny 500g", "C 1 x2,99 2,99C", "SUMA PLN 2,99"),
        )
        assertEquals(1, receipt.items.size)
        assertEquals("Tost Pszenny 500g", receipt.items[0].name)
        assertEquals(299L, receipt.items[0].price)
        assertEquals(299L, receipt.total)
    }

    @Test
    fun appliesDiscountWrittenInSeparateRow() {
        val receipt = ReceiptParser.parseRows(
            listOf("PARAGON FISKALNY", "NapGazOrango2l C 2 x3,49 6,98C", "OPUST", "-1,00C", "5,98", "SUMA PLN 5,98"),
        )
        assertEquals(598L, receipt.items.single().price)
        assertEquals(-100L, receipt.items.single().discount)
    }

    @Test
    fun toleratesSpacesInsideAmounts() {
        val receipt = ReceiptParser.parseRows(listOf("PARAGON FISKALNY", "NapGazOrango2l C 2 x3, 49 6 ,98C", "SUMA PLN 6,98"))
        assertEquals(349L, receipt.items.single().unitPrice)
        assertEquals(698L, receipt.items.single().price)
    }

    @Test
    fun readsDiscountWithOcrNoise() {
        // zero zamiast O i półpauza zamiast minusa
        val receipt = ReceiptParser.parseRows(
            listOf("PARAGON FISKALNY", "NapGazOrango2l C 2 x3,49 6,98C", "0PUST –1,00C", "SUMA PLN 5,98"),
        )
        assertEquals(598L, receipt.items.single().price)
    }

    @Test
    fun doesNotApplyTotalDiscountTwice() {
        // kwota z "OPUSTY ŁĄCZNIE" jest wiersz wyżej i nie może odjąć się drugi raz
        val receipt = ReceiptParser.parseRows(
            listOf("PARAGON FISKALNY", "NapGazOrango2l C 2 x3,49 6,98C", "OPUST -1,00C", "-1,00", "OPUSTY ŁĄCZNIE", "SUMA PLN 5,98"),
        )
        assertEquals(598L, receipt.items.single().price)
    }

    @Test
    fun readsPtuLetterGluedToQuantity() {
        val receipt = ReceiptParser.parseRows(listOf("PARAGON FISKALNY", "MANDARYNKA LUZ T B1,965x2,49 4,89B", "SUMA 4,89"))
        assertEquals("MANDARYNKA LUZ T", receipt.items.single().name)
        assertEquals("1,965", receipt.items.single().quantity)
        assertEquals(489L, receipt.items.single().price)
    }

    @Test
    fun readsTotalFromNextRow() {
        val receipt = ReceiptParser.parseRows(listOf("PARAGON FISKALNY", "Chleb C 1 x2,78 2,78C", "SUMA PLN", "2,78"))
        assertEquals(278L, receipt.total)
    }

    @Test
    fun groupsColumnsIntoRows() {
        // ML Kit oddaje osobno nazwy, litery PTU i kwoty
        val lines = listOf(
            OcrLine("Chleb 500g", left = 10, top = 100, right = 200, bottom = 130),
            OcrLine("C", left = 400, top = 102, right = 415, bottom = 132),
            OcrLine("1 x2,78 2,78C", left = 600, top = 106, right = 900, bottom = 136),
            OcrLine("Mleko 1l", left = 10, top = 150, right = 160, bottom = 180),
            OcrLine("C", left = 400, top = 152, right = 415, bottom = 182),
            OcrLine("4 x3,79 15,16C", left = 600, top = 156, right = 900, bottom = 186),
        )
        assertEquals(
            listOf("Chleb 500g C 1 x2,78 2,78C", "Mleko 1l C 4 x3,79 15,16C"),
            ReceiptParser.groupIntoRows(lines),
        )
    }

    @Test
    fun straightensSkewedPhoto() {
        // zdjęcie przekrzywione o ok. 2,3°, przez co kwoty są ok. 26 px niżej niż nazwy
        val angle = 0.04
        fun line(text: String, left: Int, right: Int, rowY: Int): OcrLine {
            val centerY = rowY + (left + right) / 2.0 * angle
            return OcrLine(text, left, (centerY - 15).toInt(), right, (centerY + 15).toInt(), angle)
        }
        val lines = listOf(
            line("Chleb 500g C", 10, 200, rowY = 100),
            line("1 x2,78 2,78C", 600, 900, rowY = 100),
            line("Mleko 1l C", 10, 200, rowY = 150),
            line("4 x3,79 15,16C", 600, 900, rowY = 150),
        )
        assertEquals(
            listOf("Chleb 500g C 1 x2,78 2,78C", "Mleko 1l C 4 x3,79 15,16C"),
            ReceiptParser.groupIntoRows(lines),
        )
    }
}
