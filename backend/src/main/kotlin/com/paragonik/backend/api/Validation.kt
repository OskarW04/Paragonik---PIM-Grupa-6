package com.paragonik.backend.api

import com.paragonik.backend.auth.Passwords
import java.util.UUID

object Validation {
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun email(raw: String): String {
        val email = raw.trim().lowercase()
        if (email.length > 254 || !emailRegex.matches(email)) badRequest("Podaj poprawny adres e-mail.")
        return email
    }

    fun password(raw: String): String {
        if (raw.length < 8 || raw.toByteArray(Charsets.UTF_8).size > Passwords.MAX_BYTES) {
            badRequest("Hasło musi mieć od 8 do ${Passwords.MAX_BYTES} znaków.")
        }
        return raw
    }

    fun displayName(raw: String): String {
        val name = raw.trim()
        if (name.length !in 2..50) badRequest("Imię musi mieć od 2 do 50 znaków.")
        return name
    }

    fun fcmToken(raw: String): String {
        val token = raw.trim()
        if (token.isEmpty() || token.length > 4096) badRequest("Token FCM jest pusty albo za długi.")
        return token
    }

    fun uuid(raw: String, field: String): UUID =
        runCatching { UUID.fromString(raw.trim()) }.getOrElse { badRequest("Pole $field musi być identyfikatorem UUID.") }
}
