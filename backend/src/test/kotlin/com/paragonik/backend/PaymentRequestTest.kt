package com.paragonik.backend

import com.paragonik.backend.api.AuthResponse
import com.paragonik.backend.api.CreatePaymentRequest
import com.paragonik.backend.api.ErrorResponse
import com.paragonik.backend.api.FcmTokenRequest
import com.paragonik.backend.api.PaymentItemDto
import com.paragonik.backend.api.PaymentRequestDto
import com.paragonik.backend.api.PersonDto
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentRequestTest {

    private suspend fun ApiClient.send(from: AuthResponse, request: CreatePaymentRequest): HttpResponse =
        http.post("/requests") { auth(from); jsonBody(request) }

    private suspend fun ApiClient.registerPhone(user: AuthResponse, token: String) {
        assertEquals(HttpStatusCode.NoContent, http.put("/me/fcm-token") { auth(user); jsonBody(FcmTokenRequest(token)) }.status)
    }

    private suspend fun ApiClient.incoming(user: AuthResponse): List<PaymentRequestDto> =
        http.get("/requests/incoming") { auth(user) }.body()

    @Test
    fun `payer sends a request and the debtor gets a push and sees it`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        val ania = register("ania@example.com", "Ania")
        val basia = register("basia@example.com", "Basia")
        registerPhone(ania, "telefon-ani")

        val response = send(
            oskar,
            CreatePaymentRequest(
                recipientId = ania.user.id,
                amount = 1000,
                shopName = "Biedronka",
                purchaseDate = "2026-11-18",
                items = listOf(PaymentItemDto("Chipsy", 500), PaymentItemDto("Płyn do naczyń (1/2)", 500)),
            ),
        )
        assertEquals(HttpStatusCode.Created, response.status)
        val created = response.body<PaymentRequestDto>()
        assertEquals(PersonDto(oskar.user.id, "Oskar"), created.sender)
        assertEquals(PersonDto(ania.user.id, "Ania"), created.recipient)
        assertEquals(1000, created.amount)
        assertEquals("Biedronka", created.shopName)
        assertEquals("2026-11-18", created.purchaseDate)
        assertEquals(listOf("Chipsy", "Płyn do naczyń (1/2)"), created.items.map { it.name })

        val push = push.next()
        assertEquals(listOf("telefon-ani"), push.tokens)
        assertEquals("Nowa prośba o zwrot", push.message.title)
        assertEquals("Oskar prosi o zwrot 10,00\u00A0zł (Biedronka, 18.11.2026)", push.message.body)
        assertEquals(created.id, push.message.data["requestId"])
        assertEquals("payment_request", push.message.data["type"])

        assertEquals(listOf(created), incoming(ania))
        assertEquals(emptyList(), incoming(oskar))

        assertEquals(created, http.get("/requests/${created.id}") { auth(ania) }.body<PaymentRequestDto>())
        assertEquals(created, http.get("/requests/${created.id}") { auth(oskar) }.body<PaymentRequestDto>())
        assertEquals(HttpStatusCode.NotFound, http.get("/requests/${created.id}") { auth(basia) }.status)
        assertEquals(HttpStatusCode.NotFound, http.get("/requests/nie-uuid") { auth(ania) }.status)
    }

    @Test
    fun `request without items and details is accepted`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        val ania = register("ania@example.com", "Ania")
        registerPhone(ania, "telefon-ani")

        val response = send(oskar, CreatePaymentRequest(recipientId = ania.user.id, amount = 1234))
        assertEquals(HttpStatusCode.Created, response.status)
        val created = response.body<PaymentRequestDto>()
        assertEquals(emptyList(), created.items)
        assertEquals(null, created.shopName)
        assertEquals("Oskar prosi o zwrot 12,34\u00A0zł", push.next().message.body)
    }

    @Test
    fun `invalid requests are rejected`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        val ania = register("ania@example.com", "Ania")

        suspend fun expectError(request: CreatePaymentRequest, status: HttpStatusCode, messagePart: String) {
            val response = send(oskar, request)
            assertEquals(status, response.status, "Dla $request")
            val error = response.body<ErrorResponse>()
            assertTrue(messagePart in error.message, "Komunikat „${error.message}” powinien zawierać „$messagePart”")
        }

        val base = CreatePaymentRequest(recipientId = ania.user.id, amount = 1000)
        expectError(
            base.copy(items = listOf(PaymentItemDto("Chipsy", 500), PaymentItemDto("Ciastka", 400))),
            HttpStatusCode.BadRequest,
            "Suma pozycji",
        )
        expectError(base.copy(recipientId = oskar.user.id), HttpStatusCode.BadRequest, "samego siebie")
        expectError(base.copy(recipientId = UUID.randomUUID().toString()), HttpStatusCode.NotFound, "odbiorcy")
        expectError(base.copy(recipientId = "abc"), HttpStatusCode.BadRequest, "recipientId")
        expectError(base.copy(amount = 0), HttpStatusCode.BadRequest, "większa od zera")
        expectError(base.copy(purchaseDate = "18.11.2026"), HttpStatusCode.BadRequest, "RRRR-MM-DD")
        expectError(base.copy(clientId = "nie-uuid"), HttpStatusCode.BadRequest, "clientId")

        assertEquals(emptyList(), incoming(ania))
        push.expectNothing()
    }

    @Test
    fun `retry with the same clientId does not create a duplicate`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        val ania = register("ania@example.com", "Ania")
        registerPhone(ania, "telefon-ani")
        val request = CreatePaymentRequest(recipientId = ania.user.id, amount = 500, clientId = UUID.randomUUID().toString())

        val first = send(oskar, request)
        val second = send(oskar, request)

        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(first.body<PaymentRequestDto>().id, second.body<PaymentRequestDto>().id)
        assertEquals(1, incoming(ania).size)
        push.next()
        push.expectNothing()
    }

    @Test
    fun `no push is sent when the debtor has no registered phone`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        val ania = register("ania@example.com", "Ania")

        assertEquals(HttpStatusCode.Created, send(oskar, CreatePaymentRequest(ania.user.id, 700)).status)
        push.expectNothing()
        assertEquals(1, incoming(ania).size)
    }

    @Test
    fun `a phone belongs to the account that registered it last`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        val ania = register("ania@example.com", "Ania")
        val basia = register("basia@example.com", "Basia")
        registerPhone(ania, "wspolny-telefon")
        registerPhone(basia, "wspolny-telefon")

        send(oskar, CreatePaymentRequest(ania.user.id, 300))
        push.expectNothing()

        send(oskar, CreatePaymentRequest(basia.user.id, 300))
        assertEquals(listOf("wspolny-telefon"), push.next().tokens)
    }

    @Test
    fun `tokens rejected by fcm are removed`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        val ania = register("ania@example.com", "Ania")
        registerPhone(ania, "stary-telefon")
        registerPhone(ania, "nowy-telefon")
        push.invalidTokens = setOf("stary-telefon")

        send(oskar, CreatePaymentRequest(ania.user.id, 300))

        assertEquals(setOf("stary-telefon", "nowy-telefon"), push.next().tokens.toSet())
        eventually { TestDatabase.deviceTokens() == listOf("nowy-telefon") }
    }

    @Test
    fun `deleting an account removes its requests`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        val ania = register("ania@example.com", "Ania")
        send(oskar, CreatePaymentRequest(ania.user.id, 300))
        assertEquals(1, incoming(ania).size)

        assertEquals(HttpStatusCode.NoContent, http.delete("/me") { auth(oskar) }.status)

        assertEquals(emptyList(), incoming(ania))
    }
}
