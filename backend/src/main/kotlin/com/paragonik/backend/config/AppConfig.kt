package com.paragonik.backend.config

import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class AppConfig(
    val port: Int,
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val fcmServiceAccountJson: String?,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            fun required(name: String): String =
                env[name]?.takeIf { it.isNotBlank() } ?: error("Brak wymaganej zmiennej środowiskowej $name")

            val jwtSecret = required("JWT_SECRET")
            require(jwtSecret.length >= 32) { "JWT_SECRET musi mieć co najmniej 32 znaki" }

            val fcmJson = env["FCM_SERVICE_ACCOUNT_BASE64"]?.takeIf { it.isNotBlank() }?.let { encoded ->
                runCatching { String(Base64.getDecoder().decode(encoded.filterNot { it.isWhitespace() }), Charsets.UTF_8) }
                    .getOrElse { throw IllegalStateException("FCM_SERVICE_ACCOUNT_BASE64 nie jest poprawnym Base64", it) }
            }

            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                database = DatabaseConfig(
                    jdbcUrl = required("DATABASE_URL"),
                    user = required("DATABASE_USER"),
                    password = required("DATABASE_PASSWORD"),
                ),
                jwt = JwtConfig(
                    secret = jwtSecret,
                    ttl = (env["JWT_TTL_DAYS"]?.toIntOrNull() ?: 30).days,
                ),
                fcmServiceAccountJson = fcmJson,
            )
        }
    }
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10,
)

data class JwtConfig(
    val secret: String,
    val issuer: String = "paragonik",
    val audience: String = "paragonik-app",
    val ttl: Duration = 30.days,
)
