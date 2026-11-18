package com.paragonik.backend.db

import com.paragonik.backend.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.DatabaseConfig as ExposedConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("com.paragonik.backend.db")

fun createDataSource(config: DatabaseConfig): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.user
        password = config.password
        maximumPoolSize = config.maxPoolSize
        poolName = "paragonik-db"
        connectionTimeout = 5_000
        initializationFailTimeout = -1
    },
)

fun waitForDatabase(dataSource: DataSource, attempts: Int = 30, delayMillis: Long = 2_000) {
    repeat(attempts) { attempt ->
        try {
            dataSource.connection.use { if (it.isValid(2)) return }
        } catch (e: SQLException) {
            log.info("Baza danych jeszcze niedostępna (próba {}/{}): {}", attempt + 1, attempts, e.message)
        }
        Thread.sleep(delayMillis)
    }
    error("Nie udało się połączyć z bazą danych")
}

fun migrate(dataSource: DataSource) {
    val result = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
    log.info("Migracje bazy danych: wykonano {}", result.migrationsExecuted)
}

fun connectExposed(dataSource: DataSource): Database =
    Database.connect(dataSource, databaseConfig = ExposedConfig { defaultMaxAttempts = 1 })

class Db(private val database: Database) {
    suspend fun <T> query(block: () -> T): T =
        withContext(Dispatchers.IO) { transaction(database) { block() } }
}

private const val UNIQUE_VIOLATION = "23505"

fun Throwable.isUniqueViolation(): Boolean =
    generateSequence(this) { it.cause }.filterIsInstance<SQLException>().any { it.sqlState == UNIQUE_VIOLATION }

fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS)
