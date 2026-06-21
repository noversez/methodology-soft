# DIMS Casebook Holmes

Монорепозиторий приложения DIMS по документам `Vision`, `SRS` и `Use-Case`.

## Стек

- Backend: Java 17 target, Spring Boot 3, Spring Web, Validation, Spring Data JPA.
- Database: PostgreSQL 14+ для локальной разработки и эксплуатации; H2 только для автоматических тестов.
- Frontend: React, TypeScript, Vite.
- API: REST + JSON, заголовок текущего пользователя `X-User-Id`.

## Локальный запуск

Сначала запустите PostgreSQL 16 с постоянным Docker volume:

```bash
docker compose up -d postgres
```

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Откройте `http://127.0.0.1:5173`.

## Seed-пользователи

Backend создает пользователей:

- `sherlock` / Detective
- `watson` / Assistant
- `lestrade` / Inspector
- `agent` / Agent
- `lab` / LabAnalyst
- `admin` / Admin

Frontend получает UUID пользователей через `/api/users` и передает выбранного пользователя в `X-User-Id`.

## Конфигурация

- `DIMS_DATABASE_URL` - JDBC URL PostgreSQL, по умолчанию `jdbc:postgresql://127.0.0.1:5433/dims`.
- `DIMS_POSTGRES_PORT` - host-порт контейнера PostgreSQL, по умолчанию `5433`.
- `DIMS_DATABASE_USERNAME`
- `DIMS_DATABASE_PASSWORD`
- `DIMS_DATABASE_POOL_SIZE` - максимальный размер пула соединений, по умолчанию 10.
- `DIMS_FILE_UPLOAD_MAX_MB` - MVP лимит 20 MB.
- `DIMS_STORAGE_PATH` - путь файлового хранилища.

Схема PostgreSQL управляется Flyway. Миграции находятся в
`backend/src/main/resources/db/migration`; Hibernate настроен на `ddl-auto: validate`
и не изменяет таблицы самостоятельно. H2 используется только в автоматических тестах.

## Документы

- `docs/PROJECT_CONTEXT.md` - консолидированный контекст продукта.
- `docs/AI_IMPLEMENTATION_CHECKLIST.md` - итеративный чеклист реализации.
