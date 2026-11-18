package com.paragonik.backend.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)

@Serializable
data class ErrorResponse(val error: String, val message: String)

fun badRequest(message: String): Nothing =
    throw ApiException(HttpStatusCode.BadRequest, "validation_error", message)

fun notFound(message: String): Nothing =
    throw ApiException(HttpStatusCode.NotFound, "not_found", message)

fun StatusPagesConfig.configureErrors() {
    exception<ApiException> { call, e ->
        call.respond(e.status, ErrorResponse(e.code, e.message))
    }
    exception<BadRequestException> { call, _ ->
        call.respond(HttpStatusCode.BadRequest, invalidBody)
    }
    exception<ContentTransformationException> { call, _ ->
        call.respond(HttpStatusCode.BadRequest, invalidBody)
    }
    exception<SerializationException> { call, _ ->
        call.respond(HttpStatusCode.BadRequest, invalidBody)
    }
    exception<UnsupportedMediaTypeException> { call, _ ->
        call.respond(
            HttpStatusCode.UnsupportedMediaType,
            ErrorResponse("unsupported_media_type", "Wyślij dane jako JSON z nagłówkiem Content-Type: application/json."),
        )
    }
    exception<Throwable> { call, e ->
        call.application.log.error("Nieobsłużony błąd: ${call.request.httpMethod.value} ${call.request.path()}", e)
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Wewnętrzny błąd serwera."))
    }
    status(HttpStatusCode.NotFound) { call, status ->
        call.respond(status, ErrorResponse("not_found", "Nie ma takiego adresu w API."))
    }
}

private val invalidBody = ErrorResponse("invalid_body", "Nieprawidłowe dane w żądaniu: sprawdź format JSON i wymagane pola.")
