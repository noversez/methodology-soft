# DIMS Casebook Holmes

Монорепозиторий приложения DIMS по документам `Vision`, `SRS` и `Use-Case`.

## Стек

- Backend: Java 17 target, Spring Boot 3, Spring Web, Validation, Spring Data JPA.
- Database: PostgreSQL 14+ для эксплуатации; H2 в PostgreSQL mode для локального старта без внешней БД.
- Frontend: React, TypeScript, Vite.
- API: REST + JSON, заголовок текущего пользователя `X-User-Id`.

## Локальный запуск

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Если Maven wrapper не добавлен, используйте локальный Maven:

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

- `DIMS_DATABASE_URL` - JDBC URL PostgreSQL.
- `DIMS_DATABASE_USERNAME`
- `DIMS_DATABASE_PASSWORD`
- `DIMS_DATABASE_DRIVER` - по умолчанию H2.
- `DIMS_FILE_UPLOAD_MAX_MB` - MVP лимит 20 MB.
- `DIMS_STORAGE_PATH` - путь файлового хранилища.

## Документы

- `docs/PROJECT_CONTEXT.md` - консолидированный контекст продукта.
- `docs/AI_IMPLEMENTATION_CHECKLIST.md` - итеративный чеклист реализации.
