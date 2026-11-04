# BehaviorGuard AI (MVP)

Система поведенческой биометрии для веб-приложений: сбор сигналов через JS SDK, поведенческие профили, оценка риска через REST API.

## Структура

```
infra/        Docker Compose: PostgreSQL 17, Redis 7
backend/      Spring Boot 4 (Java 21) — Ingestion API, Risk API
```

Позже появятся `ml-engine/` (FastAPI), `sdk/` (TypeScript) и `demo-bank/` (тестовое банковское приложение).

## Запуск инфраструктуры

```bash
docker compose -f infra/docker-compose.yml up -d
```

PostgreSQL слушает порт **5433** на хосте (5432 часто занят локальными проектами). Redis — 6379.

## Запуск backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Профиль `dev` при первом запуске создаёт демо-организацию «Demo Bank» и печатает её ключи **один раз**:
- `bg_pk_...` — публичный ключ SDK (`PUBLIC_CLIENT`);
- `bg_sk_...` — серверный ключ бэкенда банка (`SERVER`).

Сохраните их: повторно в логе они не появятся. В базе хранится только SHA-256 от ключа.

Переменные окружения (необязательны, есть значения по умолчанию): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`.

## API

Аутентификация: заголовок `X-BG-Api-Key`.

| Метод | Путь | Кто вызывает | Ответы |
|---|---|---|---|
| GET | `/actuator/health` | все | 200 |
| POST | `/api/v1/sessions` | ключ SDK | 201 — создана; 400 — неверные данные; 401 — нет ключа; 403 — ключ не SDK |

Пример:

```bash
curl -X POST localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -H "X-BG-Api-Key: bg_pk_..." \
  -d '{"userId":"client-0007","deviceType":"DESKTOP","sdkVersion":"0.1.0"}'
```

`userId` — только псевдоним: буквы, цифры и символы `. _ : -`. Email и телефон (`@`, `+`) отклоняются.

## Тесты

Тесты поднимают PostgreSQL и Redis через Testcontainers, поэтому нужен запущенный Docker.

```bash
cd backend
./mvnw test
```

## Этапы 3–4: профили и оценка риска

- `ml-engine/` — FastAPI-сервис скоринга (Isolation Forest + отклонение от профиля). См. `ml-engine/README.md`.
- Профиль строится по завершённым сессиям (алгоритм Уэлфорда, без сырых значений). Статус: `NEW` → `LEARNING` (1–4 сессии) → `READY` (от 5).
- Оценка риска возвращает `UNKNOWN`, а не низкий риск, если профиля нет, признаков мало или ML-сервис недоступен.

Эндпоинты для бэкенда банка (ключ `SERVER`):

| Метод | Путь | Назначение |
|---|---|---|
| POST | `/api/v1/risk/assess` | оценка риска по сессии |
| GET | `/api/v1/risk/{sessionId}` | последняя оценка |
| GET | `/api/v1/risk/{sessionId}/history` | все оценки, от новых к старым |
| GET | `/api/v1/users/{id}/profile` | статус профиля и статистики (без сырых значений) |

Запуск ML-сервиса перед backend: `ML_BASE_URL` по умолчанию `http://localhost:8000`.
