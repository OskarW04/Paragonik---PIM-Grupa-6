package com.paragonik.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import com.paragonik.backend.api.ApiException
import com.paragonik.backend.config.JwtConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import java.time.Instant
import java.util.UUID

const val AUTH_JWT = "auth-jwt"

class JwtService(private val config: JwtConfig) {
    private val algorithm = Algorithm.HMAC256(config.secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun issue(userId: UUID, now: Instant = Instant.now()): IssuedToken {
        val expiresAt = now.plusSeconds(config.ttl.inWholeSeconds)
        val token = JWT.create()
            .withSubject(userId.toString())
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
        return IssuedToken(token, expiresAt)
    }
}

data class IssuedToken(val token: String, val expiresAt: Instant)

data class UserPrincipal(val userId: UUID)

val ApplicationCall.userId: UUID
    get() = principal<UserPrincipal>()?.userId
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Brak ważnego tokenu – zaloguj się ponownie.")
