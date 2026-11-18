package com.paragonik.backend.push

import com.paragonik.backend.api.PaymentRequestDto
import com.paragonik.backend.db.Db
import com.paragonik.backend.db.DeviceTokens
import com.paragonik.backend.util.formatDate
import com.paragonik.backend.util.formatPln
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

class PushNotifier(
    private val sender: PushSender,
    private val db: Db,
    private val scope: CoroutineScope,
) {
    val enabled: Boolean get() = sender.enabled

    fun notifyNewRequest(request: PaymentRequestDto) {
        scope.launch {
            try {
                deliver(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Nie udało się wysłać powiadomienia o prośbie {}", request.id, e)
            }
        }
    }

    private suspend fun deliver(request: PaymentRequestDto) {
        val recipientId = UUID.fromString(request.recipient.id)
        val tokens = db.query {
            DeviceTokens.select(DeviceTokens.token)
                .where { DeviceTokens.userId eq recipientId }
                .map { it[DeviceTokens.token] }
        }
        if (tokens.isEmpty()) {
            log.info("Odbiorca {} nie ma zarejestrowanego telefonu – push pominięty", recipientId)
            return
        }
        val result = sender.send(tokens, messageFor(request))
        if (result.invalidTokens.isNotEmpty()) {
            db.query { DeviceTokens.deleteWhere { DeviceTokens.token inList result.invalidTokens } }
            log.info("Usunięto {} nieaktualnych tokenów FCM", result.invalidTokens.size)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PushNotifier::class.java)

        fun messageFor(request: PaymentRequestDto): PushMessage {
            val details = listOfNotNull(
                request.shopName,
                request.purchaseDate?.let { formatDate(LocalDate.parse(it)) },
            ).joinToString(", ")
            val body = "${request.sender.displayName} prosi o zwrot ${formatPln(request.amount)}" +
                if (details.isNotEmpty()) " ($details)" else ""
            return PushMessage(
                title = "Nowa prośba o zwrot",
                body = body,
                data = mapOf(
                    "type" to "payment_request",
                    "requestId" to request.id,
                    "senderName" to request.sender.displayName,
                    "amount" to request.amount.toString(),
                ),
            )
        }
    }
}
