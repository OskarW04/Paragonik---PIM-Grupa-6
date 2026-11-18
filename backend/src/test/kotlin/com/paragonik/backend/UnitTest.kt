package com.paragonik.backend

import com.paragonik.backend.api.PaymentRequestDto
import com.paragonik.backend.api.PersonDto
import com.paragonik.backend.config.AppConfig
import com.paragonik.backend.push.FcmPushSender
import com.paragonik.backend.push.PushMessage
import com.paragonik.backend.push.PushNotifier
import com.paragonik.backend.util.formatDate
import com.paragonik.backend.util.formatPln
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.KeyPairGenerator
import java.time.LocalDate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FormatTest {
    @Test
    fun `amounts are formatted as Polish zloty`() {
        assertEquals("10,00\u00A0zł", formatPln(1000))
        assertEquals("0,05\u00A0zł", formatPln(5))
        assertEquals("1\u00A0234,56\u00A0zł", formatPln(123456))
        assertEquals("1\u00A0000\u00A0000,00\u00A0zł", formatPln(100_000_000))
    }

    @Test
    fun `dates are formatted as day month year`() {
        assertEquals("18.11.2026", formatDate(LocalDate.of(2026, 11, 18)))
    }

    @Test
    fun `push text mentions who asks, how much and for which receipt`() {
        val request = PaymentRequestDto(
            id = "r1",
            sender = PersonDto("u1", "Oskar"),
            recipient = PersonDto("u2", "Ania"),
            amount = 1000,
            shopName = null,
            purchaseDate = "2026-11-18",
            items = emptyList(),
            createdAt = "2026-11-18T12:00:00Z",
        )
        val message = PushNotifier.messageFor(request)
        assertEquals("Oskar prosi o zwrot 10,00\u00A0zł (18.11.2026)", message.body)
        assertEquals(mapOf("type" to "payment_request", "requestId" to "r1", "senderName" to "Oskar", "amount" to "1000"), message.data)
    }
}

class ConfigTest {
    private val baseEnv = mapOf(
        "DATABASE_URL" to "jdbc:postgresql://db:5432/paragonik",
        "DATABASE_USER" to "paragonik",
        "DATABASE_PASSWORD" to "haslo",
        "JWT_SECRET" to "a".repeat(32),
    )

    @Test
    fun `reads configuration from environment`() {
        val json = """{"type":"service_account"}"""
        val config = AppConfig.fromEnv(baseEnv + ("FCM_SERVICE_ACCOUNT_BASE64" to Base64.getEncoder().encodeToString(json.toByteArray())))
        assertEquals(8080, config.port)
        assertEquals(json, config.fcmServiceAccountJson)
        assertNull(AppConfig.fromEnv(baseEnv).fcmServiceAccountJson)
    }

    @Test
    fun `missing or weak secrets stop the server`() {
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(baseEnv - "JWT_SECRET") }
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnv(baseEnv + ("JWT_SECRET" to "za-krotki")) }
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(baseEnv + ("FCM_SERVICE_ACCOUNT_BASE64" to "%%%")) }
    }
}

class FcmPushSenderTest {
    private val unregistered = """
        {"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND",
        "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}
    """.trimIndent()

    @Test
    fun `sends http v1 messages and reports dead tokens`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            val body = (request.body as TextContent).text
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                "\"token\":\"dobry\"" in body -> respond("""{"name":"projects/demo/messages/1"}""", HttpStatusCode.OK, json)
                "\"token\":\"martwy\"" in body -> respond(unregistered, HttpStatusCode.NotFound, json)
                else -> respond("""{"error":{"code":503}}""", HttpStatusCode.ServiceUnavailable, json)
            }
        }
        val sender = FcmPushSender("demo", HttpClient(engine)) { "token-dostepu" }

        val result = sender.send(
            listOf("dobry", "martwy", "chwilowo-niedostepny"),
            PushMessage("Tytuł", "Treść", mapOf("requestId" to "r1")),
        )

        assertEquals(1, result.delivered)
        assertEquals(listOf("martwy"), result.invalidTokens)
        val first = requests.first()
        assertEquals("https://fcm.googleapis.com/v1/projects/demo/messages:send", first.url.toString())
        assertEquals("Bearer token-dostepu", first.headers[HttpHeaders.Authorization])
        val message = Json.parseToJsonElement((first.body as TextContent).text).jsonObject.obj("message")
        assertEquals("dobry", message.text("token"))
        assertEquals("Tytuł", message.obj("notification").text("title"))
        assertEquals("r1", message.obj("data").text("requestId"))
        assertEquals("HIGH", message.obj("android").text("priority"))
        assertEquals("payment_requests", message.obj("android").obj("notification").text("channel_id"))
    }

    @Test
    fun `reads project id from a service account file`() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.encoded) +
            "\n-----END PRIVATE KEY-----\n"
        val serviceAccount = buildJsonObject {
            put("type", "service_account")
            put("project_id", "paragonik-test")
            put("private_key_id", "abc123")
            put("private_key", pem)
            put("client_email", "firebase-adminsdk@paragonik-test.iam.gserviceaccount.com")
            put("client_id", "1234567890")
            put("token_uri", "https://oauth2.googleapis.com/token")
        }.toString()

        val sender = FcmPushSender.fromServiceAccount(serviceAccount, HttpClient(MockEngine { respondOk() }))

        assertEquals("paragonik-test", sender.projectId)
    }

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
}
