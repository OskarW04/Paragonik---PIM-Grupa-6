package com.paragonik.backend.push

import org.slf4j.LoggerFactory

interface PushSender {
    val enabled: Boolean

    suspend fun send(tokens: List<String>, message: PushMessage): PushResult
}

data class PushMessage(val title: String, val body: String, val data: Map<String, String>)

data class PushResult(val delivered: Int, val invalidTokens: List<String>)

class DisabledPushSender : PushSender {
    override val enabled = false

    override suspend fun send(tokens: List<String>, message: PushMessage): PushResult {
        log.info("Push wyłączony – pominięto powiadomienie na {} urządzeń: {}", tokens.size, message.body)
        return PushResult(delivered = 0, invalidTokens = emptyList())
    }

    private companion object {
        val log = LoggerFactory.getLogger(DisabledPushSender::class.java)
    }
}
