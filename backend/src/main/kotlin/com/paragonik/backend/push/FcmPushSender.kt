package com.paragonik.backend.push

import com.google.auth.oauth2.ServiceAccountCredentials
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

class FcmPushSender(
    val projectId: String,
    private val http: HttpClient,
    private val accessToken: suspend () -> String,
) : PushSender {
    override val enabled = true

    private enum class Outcome { DELIVERED, INVALID_TOKEN, FAILED }

    override suspend fun send(tokens: List<String>, message: PushMessage): PushResult {
        var delivered = 0
        val invalid = mutableListOf<String>()
        for (token in tokens) {
            when (sendOne(token, message)) {
                Outcome.DELIVERED -> delivered++
                Outcome.INVALID_TOKEN -> invalid += token
                Outcome.FAILED -> Unit
            }
        }
        return PushResult(delivered, invalid)
    }

    private suspend fun sendOne(token: String, message: PushMessage): Outcome {
        return try {
            val response = http.post("https://fcm.googleapis.com/v1/projects/$projectId/messages:send") {
                bearerAuth(accessToken())
                setBody(TextContent(buildBody(token, message), ContentType.Application.Json))
            }
            when {
                response.status.isSuccess() -> Outcome.DELIVERED
                isInvalidToken(response.status, response.bodyAsText()) -> Outcome.INVALID_TOKEN
                else -> {
                    log.warn("FCM odrzucił powiadomienie: {} {}", response.status, response.bodyAsText().take(500))
                    Outcome.FAILED
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Nie udało się połączyć z FCM: {}", e.message)
            Outcome.FAILED
        }
    }

    companion object {
        const val ANDROID_CHANNEL_ID = "payment_requests"
        private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        private val log = LoggerFactory.getLogger(FcmPushSender::class.java)

        fun fromServiceAccount(serviceAccountJson: String, http: HttpClient): FcmPushSender {
            val credentials = ServiceAccountCredentials.fromStream(serviceAccountJson.byteInputStream())
            val projectId = credentials.projectId
                ?: error("W pliku konta usługi Firebase brakuje pola project_id")
            val scoped = credentials.createScoped(listOf(FCM_SCOPE))
            return FcmPushSender(projectId, http) {
                withContext(Dispatchers.IO) {
                    scoped.refreshIfExpired()
                    scoped.accessToken?.tokenValue ?: error("Nie udało się pobrać tokenu dostępu do FCM")
                }
            }
        }

        fun buildBody(token: String, message: PushMessage): String = buildJsonObject {
            putJsonObject("message") {
                put("token", token)
                putJsonObject("notification") {
                    put("title", message.title)
                    put("body", message.body)
                }
                putJsonObject("data") {
                    message.data.forEach { (key, value) -> put(key, value) }
                }
                putJsonObject("android") {
                    put("priority", "HIGH")
                    putJsonObject("notification") {
                        put("channel_id", ANDROID_CHANNEL_ID)
                    }
                }
            }
        }.toString()

        fun isInvalidToken(status: HttpStatusCode, body: String): Boolean =
            (status == HttpStatusCode.NotFound && "UNREGISTERED" in body) ||
                (status == HttpStatusCode.BadRequest && "registration token" in body)
    }
}
