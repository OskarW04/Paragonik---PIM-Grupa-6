package com.example.paragonik.api

import kotlinx.serialization.Serializable

// Te same modele co na serwerze (branch backend). Kwoty w groszach, daty jako RRRR-MM-DD.

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val expiresAt: String, val user: UserDto)

@Serializable
data class UserDto(val id: String, val email: String, val displayName: String)

@Serializable
data class UpdateProfileRequest(val displayName: String)

@Serializable
data class FcmTokenRequest(val token: String)

@Serializable
data class LogoutRequest(val fcmToken: String? = null)

@Serializable
data class PaymentItemDto(val name: String, val amount: Long)

@Serializable
data class CreatePaymentRequest(
    val recipientId: String,
    val amount: Long,
    val shopName: String? = null,
    val purchaseDate: String? = null,
    val items: List<PaymentItemDto> = emptyList(),
    val clientId: String? = null, // ten sam clientId przy ponownej wysyłce = serwer nie zrobi duplikatu
)

@Serializable
data class PersonDto(val id: String, val displayName: String)

@Serializable
data class PaymentRequestDto(
    val id: String,
    val sender: PersonDto,
    val recipient: PersonDto,
    val amount: Long,
    val shopName: String? = null,
    val purchaseDate: String? = null,
    val items: List<PaymentItemDto> = emptyList(),
    val createdAt: String,
)

@Serializable
data class ErrorResponse(val error: String, val message: String)
