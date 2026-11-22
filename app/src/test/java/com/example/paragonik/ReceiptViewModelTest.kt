package com.example.paragonik

import com.example.paragonik.api.PaymentItemDto
import com.example.paragonik.api.UserDto
import com.example.paragonik.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptViewModelTest {

    private fun readmeReceipt() = ReceiptViewModel().apply {
        addItem(ReceiptItem(id = 0, name = "Chipsy", price = 500))
        addItem(ReceiptItem(id = 0, name = "Ciastka", price = 700))
        addItem(ReceiptItem(id = 0, name = "Płyn do naczyń", price = 1000))
    }

    @Test
    fun replacingGuestKeepsItemsAssignedOnlyToMe() {
        val vm = readmeReceipt()
        val (chips, cookies, liquid) = vm.items.toList()
        vm.addPerson("Anai")
        val me = vm.people[0]
        val anai = vm.people[1]
        vm.toggle(cookies, anai)
        vm.toggle(chips, me)

        // literówka w imieniu: usuwamy gościa i dodajemy jeszcze raz
        vm.removePerson(anai)
        vm.addPerson("Ania")
        val ania = vm.people[1]

        assertFalse(vm.isAssigned(cookies, ania))
        assertTrue(vm.isAssigned(liquid, ania))
        assertTrue(vm.isAssigned(chips, ania)) // nikt przy niej nie został, więc znowu jest wspólna
    }

    @Test
    fun checkingEveryoneAgainMakesItemShared() {
        val vm = readmeReceipt()
        val chips = vm.items[0]
        vm.addPerson("Ania")
        vm.toggle(chips, vm.people[1])
        vm.toggle(chips, vm.people[1])

        vm.addPerson("Bartek")
        assertTrue(vm.isAssigned(chips, vm.people[2]))
    }

    @Test
    fun buildsRequestLikeInBackendGuide() {
        val vm = readmeReceipt()
        vm.store = "Biedronka"
        vm.date = "2026-11-18"
        val (chips, cookies, _) = vm.items.toList()
        vm.addAccount(UserDto(id = "ania-id", email = "ania@example.com", displayName = "Ania"))
        val ania = vm.people[1]
        vm.toggle(chips, vm.people[0])
        vm.toggle(cookies, ania)

        val request = vm.paymentRequests().single()
        assertEquals("ania-id", request.recipientId)
        assertEquals(1000L, request.amount)
        assertEquals("Biedronka", request.shopName)
        assertEquals("2026-11-18", request.purchaseDate)
        assertEquals(
            listOf(PaymentItemDto("Chipsy", 500), PaymentItemDto("Płyn do naczyń (1/2)", 500)),
            request.items,
        )
    }

    @Test
    fun noRequestsForGuestsOrWhenSomeoneElsePaid() {
        val vm = readmeReceipt()
        vm.addPerson("Gość")
        assertTrue(vm.paymentRequests().isEmpty())

        vm.addAccount(UserDto(id = "ania-id", email = "ania@example.com", displayName = "Ania"))
        assertEquals(1, vm.paymentRequests().size)

        vm.selectPayer(vm.people[2]) // płaci Ania, więc to ona wysyła prośby
        assertTrue(vm.paymentRequests().isEmpty())
    }

    @Test
    fun cannotGoFurtherWhenItemsDoNotMatchReceiptTotal() {
        val vm = readmeReceipt()
        vm.total = 2700
        assertNotNull(vm.next())
        assertEquals(Step.RECEIPT, vm.step)

        vm.total = 2200
        assertNull(vm.next())
        assertEquals(Step.PEOPLE, vm.step)
    }
}
