package com.paragonik.backend.routes

import com.paragonik.backend.api.HealthResponse
import com.paragonik.backend.db.Db
import com.paragonik.backend.db.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.jdbc.select

fun Route.healthRoutes(db: Db, pushEnabled: Boolean) {
    get("/health") {
        val databaseUp = try {
            db.query { Users.select(Users.id).limit(1).toList() }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        call.respond(
            if (databaseUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            HealthResponse(
                status = if (databaseUp) "ok" else "error",
                database = if (databaseUp) "up" else "down",
                push = if (pushEnabled) "enabled" else "disabled",
            ),
        )
    }
}
