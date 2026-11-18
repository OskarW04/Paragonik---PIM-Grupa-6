# Paragonik – backend

REST API aplikacji Paragonik: konta, wyszukiwanie użytkowników i prośby o zwrot z powiadomieniami push (FCM).
Kotlin, Ktor, PostgreSQL, Exposed, Flyway. Opis endpointów jest dostępny pod `/docs`.

## Uruchomienie

```bash
docker compose up --build
```

API: http://localhost:8080, PostgreSQL: `localhost:5433` (użytkownik, hasło i baza: `paragonik`).

## Testy

```bash
docker compose -f docker-compose.test.yml up --build --abort-on-container-exit --exit-code-from tests
```

## Konfiguracja

| Zmienna | Opis |
| --- | --- |
| `DATABASE_URL` | adres JDBC bazy, np. `jdbc:postgresql://db:5432/paragonik` |
| `DATABASE_USER`, `DATABASE_PASSWORD` | dane logowania do bazy |
| `JWT_SECRET` | sekret do podpisywania tokenów (min. 32 znaki) |
| `FCM_SERVICE_ACCOUNT_BASE64` | klucz konta usługi Firebase w Base64; bez niego push jest wyłączony |
| `JWT_TTL_DAYS`, `PORT` | opcjonalne, domyślnie 30 dni i 8080 |
