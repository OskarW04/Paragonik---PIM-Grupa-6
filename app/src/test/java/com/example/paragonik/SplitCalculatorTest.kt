package com.example.paragonik

import com.example.paragonik.logic.SplitCalculator
import com.example.paragonik.model.Person
import com.example.paragonik.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitCalculatorTest {

    private val me = Person(id = 0, name = "Ja", isMe = true)
    private val ania = Person(id = 1, name = "Ania")
    private val bartek = Person(id = 2, name = "Bartek")

    @Test
    fun readmeExample() {
        // przykład z README: płacę 22,00 zł, Ania oddaje 10,00 zł
        val chips = ReceiptItem(id = 1, name = "Chipsy", price = 500)
        val cookies = ReceiptItem(id = 2, name = "Ciastka", price = 700)
        val liquid = ReceiptItem(id = 3, name = "Płyn do naczyń", price = 1000)

        val result = SplitCalculator.split(
            items = listOf(chips, cookies, liquid),
            people = listOf(me, ania),
            assigned = mapOf(1L to setOf(1L), 2L to setOf(0L), 3L to setOf(0L, 1L)),
        )

        assertEquals(1200L, result.single { it.person == me }.total)
        assertEquals(1000L, result.single { it.person == ania }.total)
    }

    @Test
    fun extraGroszGoesToFirstPersonOnList() {
        // 10,00 zł na 3 osoby: 3,34 + 3,33 + 3,33
        val pizza = ReceiptItem(id = 1, name = "Pizza", price = 1000)
        val result = SplitCalculator.split(listOf(pizza), listOf(me, ania, bartek), mapOf(1L to setOf(0L, 1L, 2L)))
        assertEquals(listOf(334L, 333L, 333L), result.map { it.total })
    }

    @Test
    fun remainderIsSpreadOneGroszEach() {
        // 0,05 zł na 3 osoby: 0,02 + 0,02 + 0,01
        val gum = ReceiptItem(id = 1, name = "Guma", price = 5)
        val result = SplitCalculator.split(listOf(gum), listOf(me, ania, bartek), mapOf(1L to setOf(0L, 1L, 2L)))
        assertEquals(listOf(2L, 2L, 1L), result.map { it.total })
    }

    @Test
    fun extraGroszeRotateBetweenPeople() {
        // 3 pozycje po 1,00 zł: dodatkowy grosz za każdym razem dostaje ktoś inny
        val items = (1L..3L).map { ReceiptItem(id = it, name = "P$it", price = 100) }
        val result = SplitCalculator.split(items, listOf(me, ania, bartek), assigned = emptyMap())
        assertEquals(listOf(100L, 100L, 100L), result.map { it.total })
    }

    @Test
    fun sharedReceiptIsSplitEvenly() {
        // paragon z Biedronki 69,69 zł na 3 osoby = 3 × 23,23
        val prices = listOf(598L, 499L, 278L, 699L, 1516L, 329L, 549L, 579L, 589L, 285L, 299L, 749L)
        val items = prices.mapIndexed { index, price -> ReceiptItem(id = index + 1L, name = "P$index", price = price) }
        val result = SplitCalculator.split(items, listOf(me, ania, bartek), assigned = emptyMap())
        assertEquals(listOf(2323L, 2323L, 2323L), result.map { it.total })
    }

    @Test
    fun itemWithoutAssignmentIsShared() {
        val bread = ReceiptItem(id = 1, name = "Chleb", price = 300)
        val result = SplitCalculator.split(listOf(bread), listOf(me, ania), assigned = emptyMap())
        assertEquals(listOf(150L, 150L), result.map { it.total })
    }

    @Test
    fun negativeAmountAlsoAddsUp() {
        // ujemna kwota, np. zwrot kaucji
        val refund = ReceiptItem(id = 1, name = "Zwrot kaucji", price = -5)
        val result = SplitCalculator.split(listOf(refund), listOf(me, ania), mapOf(1L to setOf(0L, 1L)))
        assertEquals(listOf(-2L, -3L), result.map { it.total })
    }

    @Test
    fun sharesAlwaysAddUpToItemsTotal() {
        val people = listOf(me, ania, bartek)
        val items = (1..30).map { ReceiptItem(id = it.toLong(), name = "P$it", price = (it * 137L) % 1000 + 1) }
        val assigned = items.associate { item ->
            item.id to people.filterIndexed { index, _ -> index == 0 || (item.id + index) % 3 != 0L }.map { it.id }.toSet()
        }
        val result = SplitCalculator.split(items, people, assigned)
        assertEquals(items.sumOf { it.price }, result.sumOf { it.total })
    }
}
