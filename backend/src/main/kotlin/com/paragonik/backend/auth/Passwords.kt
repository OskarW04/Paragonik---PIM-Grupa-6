package com.paragonik.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt

object Passwords {
    private const val COST = 11

    const val MAX_BYTES = 72

    // przy nieistniejącym koncie też liczymy bcrypt, żeby czas odpowiedzi nie zdradzał, czy e-mail jest w bazie
    private val dummyHash: String by lazy { hash("konto-ktorego-nie-ma") }

    fun hash(password: String): String = BCrypt.withDefaults().hashToString(COST, password.toCharArray())

    fun verify(password: String, hash: String?): Boolean {
        if (password.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return false
        val verified = BCrypt.verifyer().verify(password.toCharArray(), hash ?: dummyHash).verified
        return hash != null && verified
    }
}
