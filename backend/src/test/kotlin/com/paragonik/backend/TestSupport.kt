package com.paragonik.backend

import com.paragonik.backend.api.AuthResponse
import com.paragonik.backend.api.RegisterRequest
import com.paragonik.backend.api.apiJson
import com.paragonik.backend.config.AppConfig
import com.paragonik.backend.config.DatabaseConfig
import com.paragonik.backend.config.JwtConfig
import com.paragonik.backend.push.PushMessage
import com.paragonik.backend.push.PushResult
import com.paragonik.backend.push.PushSender
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

object TestDatabase {
    val config = DatabaseConfig(
        jdbcUrl = System.getenv("TEST_DATABASE_URL") ?: "jdbc:postgresql://localhost:5433/paragonik_test",
        user = System.getenv("TEST_DATABASE_USER") ?: "paragonik",
        password = System.getenv("TEST_DATABASE_PASSWORD") ?: "paragonik",
        maxPoolSize = 4,
    )

    fun clean() = execute("TRUNCATE payment_request_items, payment_requests, device_tokens, users CASCADE")

    fun deviceTokens(): List<String> = connect().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT token FROM device_tokens ORDER BY token").use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    private fun execute(sql: String) {
        connect().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun connect() = DriverManager.getConnection(config.jdbcUrl, config.user, config.password)
}

fun testConfig() = AppConfig(
    port = 0,
    database = TestDatabase.config,
    jwt = JwtConfig(secret = "sekret-testowy-sekret-testowy-1234567890"),
    fcmServiceAccountJson = null,
)

data class SentPush(val tokens: List<String>, val message: PushMessage)

class FakePushSender : PushSender {
    override val enabled = true

    @Volatile
    var invalidTokens: Set<String> = emptySet()

    private val sent = Channel<SentPush>(Channel.UNLIMITED)

    override suspend fun send(tokens: List<String>, message: PushMessage): PushResult {
        sent.send(SentPush(tokens, message))
        return PushResult(
            delivered = tokens.count { it !in invalidTokens },
            invalidTokens = tokens.filter { it in invalidTokens },
        )
    }

    suspend fun next(): SentPush = withTimeout(5.seconds) { sent.receive() }

    suspend fun expectNothing() {
        delay(500)
        assertTrue(sent.tryReceive().isFailure, "Nie powinno być żadnego powiadomienia")
    }
}

class ApiClient(val http: HttpClient, val push: FakePushSender) {
    suspend fun register(email: String, displayName: String, password: String = "tajnehaslo123"): AuthResponse {
        val response = http.post("/auth/register") { jsonBody(RegisterRequest(email, password, displayName)) }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return response.body()
    }
}

fun apiTest(block: suspend ApiClient.() -> Unit) = testApplication {
    val push = FakePushSender()
    application { module(testConfig(), push) }
    startApplication()
    TestDatabase.clean()
    val http = createClient { install(ContentNegotiation) { json(apiJson) } }
    ApiClient(http, push).block()
}

inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

fun HttpRequestBuilder.auth(user: AuthResponse) = bearerAuth(user.token)

suspend fun eventually(check: () -> Boolean) {
    withTimeout(5.seconds) {
        while (!check()) delay(50)
    }
}
