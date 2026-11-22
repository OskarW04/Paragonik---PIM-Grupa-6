package com.example.paragonik

import com.example.paragonik.logic.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun formatsGrosze() {
        assertEquals("12,36 zł", Money.format(1236))
        assertEquals("0,05 zł", Money.format(5))
        assertEquals("-1,00 zł", Money.format(-100))
        assertEquals("1234,00 zł", Money.format(123400))
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(1236L, Money.parse("12,36"))
        assertEquals(1236L, Money.parse("12.36"))
        assertEquals(1230L, Money.parse("12,3"))
        assertEquals(1200L, Money.parse("12"))
        assertEquals(-100L, Money.parse("-1,00"))
        assertEquals(1236L, Money.parse(" 12,36 zł "))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1,234"))
        assertNull(Money.parse(""))
        assertEquals(99999999999L, Money.parse("999999999,99"))
        assertNull(Money.parse("100000000000000000"))
    }
}
