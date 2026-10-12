# MatchCV.ai — Backend

Spring Boot API behind MatchCV.ai: a user uploads a base resume, pastes a job
description, and Claude rewrites the resume against it. The rewritten result is
stored and can be downloaded as ATS-friendly DOCX or PDF.

- **Spring Boot 4.1.1 / Java 25**
- **PostgreSQL** (Neon) with Flyway migrations
- **JWT auth** — short-lived access token plus a rotating refresh token
- **Claude** via the official `anthropic-java` SDK

## Running it

Requires JDK 25 and a PostgreSQL database. The Maven wrapper is committed, so
no local Maven install is needed.

```bash
cp .env.example .env     # then fill it in — see Configuration below
./mvnw spring-boot:run   # mvnw.cmd on Windows
```

The API listens on `http://localhost:8091` by default.

```bash
./mvnw test              # full suite: in-memory H2, no network, no API key
```

## Configuration

Every setting comes from an environment variable. Locally they are read from a
gitignored `.env`; deployments inject the same names directly. `.env.example`
lists all of them.

Boot fails fast if `DB_URL`, `DATABASE_PASSWORD`, or `JWT_SECRET` are
missing — these have no defaults on purpose, so the app can never run on
credentials that are public in this repository.

| Variable | Required | Notes |
| --- | --- | --- |
| `DB_URL` | yes | JDBC URL, e.g. `jdbc:postgresql://host/neondb?sslmode=require` |
| `DATABASE_USERNAME` | no | defaults to `neondb_owner` |
| `DATABASE_PASSWORD` | yes | |
| `JWT_SECRET` | yes | must decode to ≥32 bytes for HS256: `openssl rand -base64 48` |
| `CLAUDE_API_KEY` | no | unset wires in a stub client that refuses tailoring with a clear message, so the app still boots |
| `CLAUDE_MODEL` | no | defaults to `claude-opus-5` |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | no | defaults to localhost only; set the real origin in production |
| `ADMIN_PASSWORD` | no | blank disables bootstrap-admin seeding entirely |
| `SERVER_PORT` | no | defaults to `8091` |

## API

All routes are under `/api` and require a bearer access token except `/api/auth/*`.

| | |
| --- | --- |
| `POST /api/auth/signup`, `/login`, `/refresh`, `/logout` | credentials in, token pair out |
| `GET /api/me` | the current account |
| `POST /api/resumes` | upload a base resume (multipart: PDF, DOCX, or text) |
| `GET /api/resumes`, `GET /api/resumes/{id}`, `DELETE /api/resumes/{id}` | |
| `POST /api/tailorings` | tailor a base resume against a job description |
| `GET /api/tailorings`, `GET /api/tailorings/{id}`, `DELETE /api/tailorings/{id}` | |
| `GET /api/tailorings/{id}/download?format=docx\|pdf` | rendered download |
| `GET /api/billing/plans`, `/subscription`; `POST /subscribe`, `/cancel` | free and pro plans with a monthly tailoring quota |
| `/api/admin/**` | admin console: users, roles, plans, allowances, usage stats |

`GET /actuator/health` is exposed for health checks.

## Layout

```
src/main/java/com/tailorresume/
  auth/      signup, login, refresh-token rotation
  security/  JWT filter, SecurityConfig
  user/      accounts and roles
  resume/    base-resume upload; extract/ pulls text from PDF, DOCX, plain text
  tailor/    the Claude call, prompt, JSON codec, normalizer, stored results
  render/    ATS DOCX and PDF renderers plus the download endpoint
  billing/   plans, subscriptions, monthly quota enforcement
  admin/     admin console
  config/    AppProperties — all app.* settings, validated at startup
```

### Notes on the design

- **Flyway owns the schema.** Hibernate runs with `ddl-auto: validate` and never
  alters the database. Tests use H2 with a generated schema instead, because the
  migrations are PostgreSQL-specific; booting against PostgreSQL with `validate`
  is what proves the entities and the SQL still agree.
- **No API key means a stub, not a crash.** With `CLAUDE_API_KEY` unset the app
  boots and tailoring requests fail with a clear "not configured" error, which
  keeps the whole test suite runnable offline and unpaid.
- **The first admin is seeded on boot**, because reaching the admin console
  requires already being an admin. It refuses to run when any admin already
  exists, and never promotes an existing account.
