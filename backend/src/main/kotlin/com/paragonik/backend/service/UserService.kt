package com.paragonik.backend.service

import com.paragonik.backend.api.ApiException
import com.paragonik.backend.api.UserDto
import com.paragonik.backend.api.notFound
import com.paragonik.backend.auth.Passwords
import com.paragonik.backend.db.Db
import com.paragonik.backend.db.DeviceTokens
import com.paragonik.backend.db.Users
import com.paragonik.backend.db.isUniqueViolation
import com.paragonik.backend.db.nowUtc
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.UUID

class UserService(private val db: Db) {

    suspend fun register(email: String, password: String, displayName: String): UserDto {
        val hash = withContext(Dispatchers.Default) { Passwords.hash(password) }
        val newId = UUID.randomUUID()
        try {
            db.query {
                Users.insert {
                    it[Users.id] = newId
                    it[Users.email] = email
                    it[Users.displayName] = displayName
                    it[Users.passwordHash] = hash
                    it[Users.createdAt] = nowUtc()
                }
            }
        } catch (e: Exception) {
            if (e.isUniqueViolation()) {
                throw ApiException(HttpStatusCode.Conflict, "email_taken", "Konto z tym adresem e-mail już istnieje.")
            }
            throw e
        }
        return UserDto(newId.toString(), email, displayName)
    }

    suspend fun authenticate(email: String, password: String): UserDto? {
        val row = db.query { Users.selectAll().where { Users.email eq email }.singleOrNull() }
        val valid = withContext(Dispatchers.Default) { Passwords.verify(password, row?.get(Users.passwordHash)) }
        return if (valid) row?.toUserDto() else null
    }

    suspend fun exists(userId: UUID): Boolean =
        db.query { !Users.select(Users.id).where { Users.id eq userId }.empty() }

    suspend fun find(userId: UUID): UserDto? =
        db.query { Users.selectAll().where { Users.id eq userId }.singleOrNull()?.toUserDto() }

    suspend fun rename(userId: UUID, displayName: String): UserDto {
        db.query { Users.update({ Users.id eq userId }) { it[Users.displayName] = displayName } }
        return find(userId) ?: notFound("Nie znaleziono konta.")
    }

    suspend fun delete(userId: UUID) {
        db.query { Users.deleteWhere { Users.id eq userId } }
    }

    suspend fun search(query: String, excludeUserId: UUID, limit: Int = 20): List<UserDto> {
        val pattern = "%${escapeLike(query.lowercase())}%"
        return db.query {
            Users.selectAll()
                .where {
                    ((Users.displayName.lowerCase() like pattern) or (Users.email like pattern)) and
                        (Users.id neq excludeUserId)
                }
                .orderBy(Users.displayName, SortOrder.ASC)
                .limit(limit)
                .map { it.toUserDto() }
        }
    }

    suspend fun saveDeviceToken(userId: UUID, fcmToken: String) {
        db.query {
            DeviceTokens.upsert(DeviceTokens.token) {
                it[DeviceTokens.token] = fcmToken
                it[DeviceTokens.userId] = userId
                it[DeviceTokens.updatedAt] = nowUtc()
            }
        }
    }

    suspend fun removeDeviceToken(userId: UUID, fcmToken: String) {
        db.query { DeviceTokens.deleteWhere { (DeviceTokens.token eq fcmToken) and (DeviceTokens.userId eq userId) } }
    }

    private fun ResultRow.toUserDto() = UserDto(
        id = this[Users.id].toString(),
        email = this[Users.email],
        displayName = this[Users.displayName],
    )

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
