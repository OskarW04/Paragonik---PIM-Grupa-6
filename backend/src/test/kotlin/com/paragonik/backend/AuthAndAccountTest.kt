package com.paragonik.backend

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.paragonik.backend.api.AuthResponse
import com.paragonik.backend.api.ErrorResponse
import com.paragonik.backend.api.FcmTokenRequest
import com.paragonik.backend.api.HealthResponse
import com.paragonik.backend.api.LoginRequest
import com.paragonik.backend.api.LogoutRequest
import com.paragonik.backend.api.RegisterRequest
import com.paragonik.backend.api.UpdateProfileRequest
import com.paragonik.backend.api.UserDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthAndAccountTest {

    @Test
    fun `health reports database and push status`() = apiTest {
        val response = http.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HealthResponse(status = "ok", database = "up", push = "enabled"), response.body())
    }

    @Test
    fun `swagger documentation is served`() = apiTest {
        assertEquals(HttpStatusCode.OK, http.get("/docs").status)
        val spec = http.get("/docs/documentation.yaml")
        assertEquals(HttpStatusCode.OK, spec.status)
        assertTrue("/requests/incoming" in spec.bodyAsText())
    }

    @Test
    fun `registration returns a working token and normalizes email`() = apiTest {
        val ania = register("  Ania@Example.com ", "Ania")
        assertEquals("ania@example.com", ania.user.email)
        assertTrue(ania.expiresAt.isNotBlank())

        val me = http.get("/me") { auth(ania) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(ania.user, me.body<UserDto>())
    }

    @Test
    fun `email can be registered only once regardless of letter case`() = apiTest {
        register("ania@example.com", "Ania")
        val response = http.post("/auth/register") {
            jsonBody(RegisterRequest("ANIA@example.com", "innehaslo123", "Druga Ania"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("email_taken", response.body<ErrorResponse>().error)
    }

    @Test
    fun `invalid registration data is rejected with a message`() = apiTest {
        val cases = listOf(
            RegisterRequest("to-nie-email", "tajnehaslo123", "Ania"),
            RegisterRequest("ania@example.com", "krotkie", "Ania"),
            RegisterRequest("ania@example.com", "tajnehaslo123", "A"),
        )
        for (case in cases) {
            val response = http.post("/auth/register") { jsonBody(case) }
            assertEquals(HttpStatusCode.BadRequest, response.status, "Dla $case")
            val error = response.body<ErrorResponse>()
            assertEquals("validation_error", error.error)
            assertTrue(error.message.isNotBlank())
        }

        val missingField = http.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"ania@example.com"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, missingField.status)
        assertEquals("invalid_body", missingField.body<ErrorResponse>().error)
    }

    @Test
    fun `login accepts correct password only`() = apiTest {
        register("ania@example.com", "Ania", password = "tajnehaslo123")

        val ok = http.post("/auth/login") { jsonBody(LoginRequest("Ania@example.com", "tajnehaslo123")) }
        assertEquals(HttpStatusCode.OK, ok.status)
        val token = ok.body<AuthResponse>()
        assertEquals(HttpStatusCode.OK, http.get("/me") { auth(token) }.status)

        val wrongPassword = http.post("/auth/login") { jsonBody(LoginRequest("ania@example.com", "zlehaslo123")) }
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals("invalid_credentials", wrongPassword.body<ErrorResponse>().error)

        val unknownEmail = http.post("/auth/login") { jsonBody(LoginRequest("nikt@example.com", "tajnehaslo123")) }
        assertEquals(HttpStatusCode.Unauthorized, unknownEmail.status)
        assertEquals("invalid_credentials", unknownEmail.body<ErrorResponse>().error)
    }

    @Test
    fun `protected endpoints need a valid token`() = apiTest {
        val ania = register("ania@example.com", "Ania")

        val noToken = http.get("/me")
        assertEquals(HttpStatusCode.Unauthorized, noToken.status)
        assertEquals("unauthorized", noToken.body<ErrorResponse>().error)

        assertEquals(HttpStatusCode.Unauthorized, http.get("/me") { bearerAuth("to-nie-jest-jwt") }.status)

        val forged = JWT.create()
            .withSubject(ania.user.id)
            .withIssuer("paragonik")
            .withAudience("paragonik-app")
            .sign(Algorithm.HMAC256("inny-sekret-inny-sekret-inny-sekret-123"))
        assertEquals(HttpStatusCode.Unauthorized, http.get("/me") { bearerAuth(forged) }.status)
    }

    @Test
    fun `profile name can be changed`() = apiTest {
        val ania = register("ania@example.com", "Ania")
        val response = http.patch("/me") { auth(ania); jsonBody(UpdateProfileRequest("  Ania K.  ")) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ania K.", response.body<UserDto>().displayName)
    }

    @Test
    fun `search finds people by name or email and skips the caller`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        register("ania@example.com", "Ania Kowalska")
        register("anna@test.pl", "Anna")
        register("basia@test.pl", "Basia")

        suspend fun search(query: String): List<String> =
            http.get("/users") { auth(oskar); url.parameters.append("q", query) }.body<List<UserDto>>().map { it.displayName }

        assertEquals(listOf("Ania Kowalska", "Anna"), search("an"))
        assertEquals(listOf("Basia"), search("BASIA@test"))
        assertEquals(listOf("Anna", "Basia"), search("test.pl"))
        assertEquals(emptyList(), search("a"))
        assertEquals(emptyList(), search("%%"))
        assertEquals(emptyList(), search("oskar"))
    }

    @Test
    fun `fcm token is saved and removed on logout`() = apiTest {
        val ania = register("ania@example.com", "Ania")

        val saved = http.put("/me/fcm-token") { auth(ania); jsonBody(FcmTokenRequest("telefon-ani")) }
        assertEquals(HttpStatusCode.NoContent, saved.status)
        assertEquals(listOf("telefon-ani"), TestDatabase.deviceTokens())

        val logout = http.post("/auth/logout") { auth(ania); jsonBody(LogoutRequest(fcmToken = "telefon-ani")) }
        assertEquals(HttpStatusCode.NoContent, logout.status)
        assertEquals(emptyList(), TestDatabase.deviceTokens())

        assertEquals(HttpStatusCode.NoContent, http.post("/auth/logout") { auth(ania) }.status)
    }

    @Test
    fun `deleted account cannot log in and disappears from search`() = apiTest {
        val oskar = register("oskar@example.com", "Oskar")
        val ania = register("ania@example.com", "Ania", password = "tajnehaslo123")
        http.put("/me/fcm-token") { auth(ania); jsonBody(FcmTokenRequest("telefon-ani")) }

        assertEquals(HttpStatusCode.NoContent, http.delete("/me") { auth(ania) }.status)

        assertEquals(HttpStatusCode.Unauthorized, http.get("/me") { auth(ania) }.status)
        val login = http.post("/auth/login") { jsonBody(LoginRequest("ania@example.com", "tajnehaslo123")) }
        assertEquals(HttpStatusCode.Unauthorized, login.status)
        val found = http.get("/users") { auth(oskar); url.parameters.append("q", "ania") }.body<List<UserDto>>()
        assertEquals(emptyList(), found)
        assertEquals(emptyList(), TestDatabase.deviceTokens())
    }

    @Test
    fun `unknown address returns json 404`() = apiTest {
        val response = http.get("/nie-ma-takiego")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("not_found", response.body<ErrorResponse>().error)
    }
}
