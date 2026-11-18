package com.paragonik.backend.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object Users : Table("users") {
    val id = javaUUID("id")
    val email = varchar("email", 254)
    val displayName = varchar("display_name", 50)
    val passwordHash = varchar("password_hash", 100)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object DeviceTokens : Table("device_tokens") {
    val token = varchar("token", 4096)
    val userId = javaUUID("user_id")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(token)
}

object PaymentRequests : Table("payment_requests") {
    val id = javaUUID("id")
    val senderId = javaUUID("sender_id")
    val recipientId = javaUUID("recipient_id")
    val clientId = javaUUID("client_id").nullable()
    val amount = long("amount") // w groszach
    val shopName = varchar("shop_name", 100).nullable()
    val purchaseDate = date("purchase_date").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object PaymentRequestItems : Table("payment_request_items") {
    val requestId = javaUUID("request_id")
    val lineNo = integer("line_no")
    val name = varchar("name", 200)
    val amount = long("amount") // w groszach

    override val primaryKey = PrimaryKey(requestId, lineNo)
}
