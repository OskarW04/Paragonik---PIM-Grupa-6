package com.paragonik.backend.routes

import com.paragonik.backend.api.ApiException
import com.paragonik.backend.api.AuthResponse
import com.paragonik.backend.api.LoginRequest
import com.paragonik.backend.api.LogoutRequest
import com.paragonik.backend.api.RegisterRequest
import com.paragonik.backend.api.UserDto
import com.paragonik.backend.api.Validation
import com.paragonik.backend.api.apiJson
import com.paragonik.backend.auth.AUTH_JWT
import com.paragonik.backend.auth.JwtService
import com.paragonik.backend.auth.userId
import com.paragonik.backend.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.authRoutes(users: UserService, jwt: JwtService) {
    route("/auth") {
        post("/register") {
            val body = call.receive<RegisterRequest>()
            val user = users.register(
                email = Validation.email(body.email),
                password = Validation.password(body.password),
                displayName = Validation.displayName(body.displayName),
            )
            call.respond(HttpStatusCode.Created, jwt.authResponse(user))
        }

        post("/login") {
            val body = call.receive<LoginRequest>()
            val user = users.authenticate(body.email.trim().lowercase(), body.password)
                ?: throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Nieprawidłowy e-mail lub hasło.")
            call.respond(jwt.authResponse(user))
        }

        authenticate(AUTH_JWT) {
            post("/logout") {
                val text = call.receiveText()
                val body = if (text.isBlank()) LogoutRequest() else apiJson.decodeFromString<LogoutRequest>(text)
                body.fcmToken?.trim()?.takeIf { it.isNotEmpty() }?.let { users.removeDeviceToken(call.userId, it) }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun JwtService.authResponse(user: UserDto): AuthResponse {
    val issued = issue(UUID.fromString(user.id))
    return AuthResponse(token = issued.token, expiresAt = issued.expiresAt.toString(), user = user)
}
