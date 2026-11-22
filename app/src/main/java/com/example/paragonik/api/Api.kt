package com.example.paragonik.api

import android.content.Context
import com.example.paragonik.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import java.io.IOException

// Endpointy backendu, opis wszystkich: https://api-paragonik.oskarwitek.com/docs
interface ParagonikApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest)

    @PATCH("me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): UserDto

    @DELETE("me")
    suspend fun deleteAccount()

    @PUT("me/fcm-token")
    suspend fun saveFcmToken(@Body body: FcmTokenRequest)

    @GET("users")
    suspend fun searchUsers(@Query("q") query: String): List<UserDto>

    @POST("requests")
    suspend fun sendRequest(@Body body: CreatePaymentRequest): PaymentRequestDto

    @GET("requests/incoming")
    suspend fun incomingRequests(): List<PaymentRequestDto>
}

val apiJson = Json { ignoreUnknownKeys = true }

object Api {
    lateinit var service: ParagonikApi
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = Session.token
                val request = chain.request().newBuilder()
                    .apply { if (token != null) header("Authorization", "Bearer $token") }
                    .build()
                val response = chain.proceed(request)
                // 401 mimo tokenu = token wygasł albo konto usunięto, więc wylogowujemy
                if (response.code == 401 && token != null) {
                    runBlocking { Session.clear(appContext) }
                }
                response
            }
            .build()
        service = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ParagonikApi::class.java)
    }
}

// Tekst błędu do pokazania w aplikacji. Serwer opisuje błędy po polsku w polu "message".
fun Throwable.userMessage(): String = when (this) {
    is HttpException -> response()?.errorBody()?.string()
        ?.let { body -> runCatching { apiJson.decodeFromString(ErrorResponse.serializer(), body).message }.getOrNull() }
        ?: "Błąd serwera (${code()})."
    is IOException -> "Brak połączenia z serwerem."
    else -> "Coś poszło nie tak."
}
