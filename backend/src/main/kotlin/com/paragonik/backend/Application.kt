package com.paragonik.backend

import com.paragonik.backend.api.ErrorResponse
import com.paragonik.backend.api.apiJson
import com.paragonik.backend.api.configureErrors
import com.paragonik.backend.auth.AUTH_JWT
import com.paragonik.backend.auth.JwtService
import com.paragonik.backend.auth.UserPrincipal
import com.paragonik.backend.config.AppConfig
import com.paragonik.backend.db.Db
import com.paragonik.backend.db.connectExposed
import com.paragonik.backend.db.createDataSource
import com.paragonik.backend.db.migrate
import com.paragonik.backend.db.waitForDatabase
import com.paragonik.backend.push.DisabledPushSender
import com.paragonik.backend.push.FcmPushSender
import com.paragonik.backend.push.PushNotifier
import com.paragonik.backend.push.PushSender
import com.paragonik.backend.routes.accountRoutes
import com.paragonik.backend.routes.authRoutes
import com.paragonik.backend.routes.healthRoutes
import com.paragonik.backend.routes.requestRoutes
import com.paragonik.backend.service.RequestService
import com.paragonik.backend.service.UserService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.event.Level
import java.util.UUID

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(config: AppConfig, pushSenderOverride: PushSender? = null) {
    val dataSource = createDataSource(config.database)
    monitor.subscribe(ApplicationStopped) { dataSource.close() }
    waitForDatabase(dataSource)
    migrate(dataSource)
    val db = Db(connectExposed(dataSource))

    val users = UserService(db)
    val requests = RequestService(db)
    val jwtService = JwtService(config.jwt)
    val notifier = PushNotifier(pushSenderOverride ?: createPushSender(config), db, this)

    install(ContentNegotiation) { json(apiJson) }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path() != "/health" }
    }
    install(StatusPages) { configureErrors() }
    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = "paragonik"
            verifier(jwtService.verifier)
            validate { credential ->
                val userId = credential.payload.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (userId != null && users.exists(userId)) UserPrincipal(userId) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Brak ważnego tokenu – zaloguj się ponownie."),
                )
            }
        }
    }

    routing {
        get("/") { call.respondRedirect("/docs") }
        swaggerUI(path = "docs", swaggerFile = "openapi/documentation.yaml")
        healthRoutes(db, notifier.enabled)
        authRoutes(users, jwtService)
        authenticate(AUTH_JWT) {
            accountRoutes(users)
            requestRoutes(requests, notifier)
        }
    }
}

private fun Application.createPushSender(config: AppConfig): PushSender {
    val serviceAccountJson = config.fcmServiceAccountJson
    if (serviceAccountJson == null) {
        log.warn("FCM_SERVICE_ACCOUNT_BASE64 nie jest ustawione – powiadomienia push są wyłączone")
        return DisabledPushSender()
    }
    val http = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    }
    monitor.subscribe(ApplicationStopped) { http.close() }
    return FcmPushSender.fromServiceAccount(serviceAccountJson, http).also {
        log.info("Powiadomienia push przez FCM włączone (projekt Firebase: {})", it.projectId)
    }
}
