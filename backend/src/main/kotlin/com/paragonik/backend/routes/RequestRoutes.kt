package com.paragonik.backend.routes

import com.paragonik.backend.api.CreatePaymentRequest
import com.paragonik.backend.api.notFound
import com.paragonik.backend.auth.userId
import com.paragonik.backend.push.PushNotifier
import com.paragonik.backend.service.RequestService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.requestRoutes(requests: RequestService, notifier: PushNotifier) {
    route("/requests") {
        post {
            val body = call.receive<CreatePaymentRequest>()
            val result = requests.create(call.userId, body)
            if (result.created) notifier.notifyNewRequest(result.request)
            call.respond(if (result.created) HttpStatusCode.Created else HttpStatusCode.OK, result.request)
        }

        get("/incoming") {
            call.respond(requests.incoming(call.userId))
        }

        get("/{id}") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: notFound("Nie znaleziono prośby o zwrot.")
            call.respond(requests.get(id, call.userId))
        }
    }
}
