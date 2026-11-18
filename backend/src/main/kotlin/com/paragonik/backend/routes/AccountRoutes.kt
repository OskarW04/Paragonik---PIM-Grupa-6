package com.paragonik.backend.routes

import com.paragonik.backend.api.FcmTokenRequest
import com.paragonik.backend.api.UpdateProfileRequest
import com.paragonik.backend.api.UserDto
import com.paragonik.backend.api.Validation
import com.paragonik.backend.api.notFound
import com.paragonik.backend.auth.userId
import com.paragonik.backend.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.accountRoutes(users: UserService) {
    route("/me") {
        get {
            call.respond(users.find(call.userId) ?: notFound("Nie znaleziono konta."))
        }

        patch {
            val body = call.receive<UpdateProfileRequest>()
            call.respond(users.rename(call.userId, Validation.displayName(body.displayName)))
        }

        delete {
            users.delete(call.userId)
            call.respond(HttpStatusCode.NoContent)
        }

        put("/fcm-token") {
            val body = call.receive<FcmTokenRequest>()
            users.saveDeviceToken(call.userId, Validation.fcmToken(body.token))
            call.respond(HttpStatusCode.NoContent)
        }
    }

    get("/users") {
        val query = call.request.queryParameters["q"].orEmpty().trim()
        val results: List<UserDto> = if (query.length < 2) emptyList() else users.search(query, call.userId)
        call.respond(results)
    }
}
