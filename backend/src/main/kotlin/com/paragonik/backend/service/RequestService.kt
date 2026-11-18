package com.paragonik.backend.service

import com.paragonik.backend.api.CreatePaymentRequest
import com.paragonik.backend.api.PaymentItemDto
import com.paragonik.backend.api.PaymentRequestDto
import com.paragonik.backend.api.PersonDto
import com.paragonik.backend.api.Validation
import com.paragonik.backend.api.badRequest
import com.paragonik.backend.api.notFound
import com.paragonik.backend.db.Db
import com.paragonik.backend.db.PaymentRequestItems
import com.paragonik.backend.db.PaymentRequests
import com.paragonik.backend.db.Users
import com.paragonik.backend.db.isUniqueViolation
import com.paragonik.backend.db.nowUtc
import com.paragonik.backend.util.formatPln
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.LocalDate
import java.util.UUID

class RequestService(private val db: Db) {

    data class CreateResult(val request: PaymentRequestDto, val created: Boolean)

    private data class NewRequest(
        val recipientId: UUID,
        val clientId: UUID?,
        val amount: Long,
        val shopName: String?,
        val purchaseDate: LocalDate?,
        val items: List<PaymentItemDto>,
    )

    suspend fun create(senderId: UUID, input: CreatePaymentRequest): CreateResult {
        val data = validate(senderId, input)
        return try {
            db.query { insertOrFind(senderId, data) }
        } catch (e: Exception) {
            val existing = if (data.clientId != null && e.isUniqueViolation()) {
                db.query { findByClientId(senderId, data.clientId) }
            } else {
                null
            }
            existing?.let { CreateResult(it, created = false) } ?: throw e
        }
    }

    suspend fun incoming(recipientId: UUID): List<PaymentRequestDto> =
        db.query { load({ PaymentRequests.recipientId eq recipientId }, limit = 100) }

    suspend fun get(requestId: UUID, viewerId: UUID): PaymentRequestDto =
        db.query {
            load({
                (PaymentRequests.id eq requestId) and
                    ((PaymentRequests.senderId eq viewerId) or (PaymentRequests.recipientId eq viewerId))
            }).singleOrNull()
        } ?: notFound("Nie znaleziono prośby o zwrot.")

    private fun validate(senderId: UUID, input: CreatePaymentRequest): NewRequest {
        val recipientId = Validation.uuid(input.recipientId, "recipientId")
        if (recipientId == senderId) badRequest("Nie możesz wysłać prośby o zwrot do samego siebie.")
        if (input.amount <= 0) badRequest("Kwota musi być większa od zera.")
        if (input.amount > MAX_AMOUNT) badRequest("Kwota jest za duża.")

        val shopName = input.shopName?.trim()?.takeIf { it.isNotEmpty() }
        if (shopName != null && shopName.length > 100) badRequest("Nazwa sklepu może mieć najwyżej 100 znaków.")

        val purchaseDate = input.purchaseDate?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalDate.parse(it) }.getOrElse { badRequest("Data zakupu musi mieć format RRRR-MM-DD.") }
        }

        if (input.items.size > MAX_ITEMS) badRequest("Prośba może mieć najwyżej $MAX_ITEMS pozycji.")
        val items = input.items.map { item ->
            val name = item.name.trim()
            if (name.isEmpty() || name.length > 200) badRequest("Nazwa pozycji musi mieć od 1 do 200 znaków.")
            if (item.amount !in 0L..MAX_AMOUNT) badRequest("Kwota pozycji „$name” jest nieprawidłowa.")
            PaymentItemDto(name, item.amount)
        }
        val itemsSum = items.sumOf { it.amount }
        if (items.isNotEmpty() && itemsSum != input.amount) {
            badRequest("Suma pozycji (${formatPln(itemsSum)}) nie zgadza się z kwotą prośby (${formatPln(input.amount)}).")
        }

        val clientId = input.clientId?.takeIf { it.isNotBlank() }?.let { Validation.uuid(it, "clientId") }
        return NewRequest(recipientId, clientId, input.amount, shopName, purchaseDate, items)
    }

    private fun insertOrFind(senderId: UUID, data: NewRequest): CreateResult {
        if (data.clientId != null) {
            findByClientId(senderId, data.clientId)?.let { return CreateResult(it, created = false) }
        }
        if (Users.select(Users.id).where { Users.id eq data.recipientId }.empty()) {
            notFound("Nie znaleziono odbiorcy prośby.")
        }

        val requestId = UUID.randomUUID()
        PaymentRequests.insert {
            it[PaymentRequests.id] = requestId
            it[PaymentRequests.senderId] = senderId
            it[PaymentRequests.recipientId] = data.recipientId
            it[PaymentRequests.clientId] = data.clientId
            it[PaymentRequests.amount] = data.amount
            it[PaymentRequests.shopName] = data.shopName
            it[PaymentRequests.purchaseDate] = data.purchaseDate
            it[PaymentRequests.createdAt] = nowUtc()
        }
        PaymentRequestItems.batchInsert(data.items.withIndex(), shouldReturnGeneratedValues = false) { (index, item) ->
            this[PaymentRequestItems.requestId] = requestId
            this[PaymentRequestItems.lineNo] = index
            this[PaymentRequestItems.name] = item.name
            this[PaymentRequestItems.amount] = item.amount
        }
        return CreateResult(load({ PaymentRequests.id eq requestId }).single(), created = true)
    }

    private fun findByClientId(senderId: UUID, clientId: UUID): PaymentRequestDto? =
        load({ (PaymentRequests.senderId eq senderId) and (PaymentRequests.clientId eq clientId) }).singleOrNull()

    private fun load(condition: () -> Op<Boolean>, limit: Int? = null): List<PaymentRequestDto> {
        val query = PaymentRequests.selectAll()
            .where(condition)
            .orderBy(PaymentRequests.createdAt to SortOrder.DESC, PaymentRequests.id to SortOrder.ASC)
        val rows = (if (limit != null) query.limit(limit) else query).toList()
        if (rows.isEmpty()) return emptyList()

        val requestIds = rows.map { it[PaymentRequests.id] }
        val userIds = rows.flatMap { listOf(it[PaymentRequests.senderId], it[PaymentRequests.recipientId]) }.distinct()
        val names = Users.select(Users.id, Users.displayName)
            .where { Users.id inList userIds }
            .associate { it[Users.id] to it[Users.displayName] }
        val items = PaymentRequestItems.selectAll()
            .where { PaymentRequestItems.requestId inList requestIds }
            .orderBy(PaymentRequestItems.lineNo, SortOrder.ASC)
            .groupBy(
                { it[PaymentRequestItems.requestId] },
                { PaymentItemDto(it[PaymentRequestItems.name], it[PaymentRequestItems.amount]) },
            )

        return rows.map { row ->
            val id = row[PaymentRequests.id]
            val senderId = row[PaymentRequests.senderId]
            val recipientId = row[PaymentRequests.recipientId]
            PaymentRequestDto(
                id = id.toString(),
                sender = PersonDto(senderId.toString(), names[senderId].orEmpty()),
                recipient = PersonDto(recipientId.toString(), names[recipientId].orEmpty()),
                amount = row[PaymentRequests.amount],
                shopName = row[PaymentRequests.shopName],
                purchaseDate = row[PaymentRequests.purchaseDate]?.toString(),
                items = items[id].orEmpty(),
                createdAt = row[PaymentRequests.createdAt].toInstant().toString(),
            )
        }
    }

    companion object {
        const val MAX_AMOUNT = 100_000_000L
        const val MAX_ITEMS = 200
    }
}
