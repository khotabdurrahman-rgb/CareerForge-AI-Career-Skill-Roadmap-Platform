# Configuration and security

The default H2 application starts without an OpenAI API key or SMTP configuration. Core skill-gap recommendations are deterministic. Optional services report availability and return explicit unavailable responses when unconfigured; no simulated provider-success claim is expected.

## Environment

`.env.example` is a reference; Spring Boot and the launcher do not automatically load it. Set environment variables in the process starting Java. Never commit populated credentials.

| Variable | Default / role |
| --- | --- |
| `PORT` | `8090` |
| `SEED_DEMO` | `true`; disable demo accounts with `false` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection; see database guide |
| `OPENAI_API_KEY` | Empty; optional server-side mentor credential |
| `OPENAI_MODEL` | `gpt-4.1-mini` |
| `SMTP_HOST` | Empty; SMTP unavailable until configured |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Empty; provider credentials |
| `SMTP_AUTH`, `SMTP_STARTTLS` | `true` |
| `MAIL_FROM` | `careerforge@localhost`; replace for real delivery |
| `APP_BASE_URL` | Email-link origin; defaults to Render's `RENDER_EXTERNAL_URL`, otherwise `http://localhost:8090` |
| `AUTH_LOGIN_LIMIT`, `AUTH_LOGIN_WINDOW_MS` | 20 sign-in requests per client IP per 900000 ms (15 minutes) |
| `AUTH_REGISTRATION_LIMIT`, `AUTH_REGISTRATION_WINDOW_MS` | 10 registration requests per client IP per 3600000 ms (one hour) |

For private local mail testing, from the workspace:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'smtp-file'
.\scripts\start.ps1
```

Alternatively use Maven with `-Dspring-boot.run.profiles=smtp-file`; combine with `mysql` when needed. The file profile writes under `./data/mail`, resolving to `backend/data/mail` from the backend working directory. Messages contain raw recovery/verification links and are private credentials: read them locally, restrict file access, and exclude them from screenshots and version control. They demonstrate file delivery, not actual SMTP delivery. Tokens must never appear in normal UI/API responses.

## Account and request controls

Passwords use BCrypt; password hashes and session versions are omitted from normal account JSON. Login rotates the session; logout invalidates it. Session cookies are HttpOnly/SameSite Strict with a 60-minute idle timeout. Password reset increments the user's session version so existing sessions can be rejected. Account tokens have a unique hash, purpose, expiry, and used flag; raw tokens belong only in email/private local files. Account-discovery responses stay generic.

Owned records require ownership checks; administrator routes require `ADMIN`. The request filter rejects cross-site mutations. Use the application's same-origin URL and retain cookies in Postman. HTTPS and deployment controls require environment-specific setup. These descriptions do not claim a security audit.

Login/registration limits apply before JSON validation, count successful and failed requests, and return JSON 429 plus `Retry-After` seconds. Buckets are separate by operation and client IP; rotating email addresses or cookies does not reset them. The limiter caps active buckets at 10000 and uses each bucket's own expiry. It is in-memory, resets on process restart, and is not distributed across instances. School networks sharing one public IP may need higher configured budgets. For multiple instances, use a shared limiter or edge protection. Forwarded headers must only be accepted behind the host's trusted proxy; never expose the public-profile port directly with arbitrary forwarded headers.

## Health and monitoring

Only `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` are publicly exposed. Group probes contain status only; the root also lists group names, without component, database, filesystem, or credential details. Readiness includes the database; liveness does not. SMTP and AI are optional and are not contacted by these health probes. Environment, metrics, heap dumps, and endpoint discovery are not exposed. See [Spring Boot health documentation](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html).

The internal Micrometer counter `careerforge.auth.rate_limited` uses only the bounded operation label (`login` or `registration`), never email/IP labels. Public-profile web/SQL logging is restrained to avoid logging form bodies and queries at DEBUG. No external alerting account has been provisioned; configure your host's health check and an uptime alert separately.

## AI data flow

The optional mentor uses server-side Java `HttpClient` REST calls with `store:false`, documented by the [official Responses create reference](https://developers.openai.com/api/reference/python/resources/responses/methods/create). This option does not stop CareerForge from persisting local `mentor_messages` and daily `mentor_usage`. Prompts/context go to OpenAI; avoid credentials and unnecessary sensitive data. No real API key is included or used for this documentation work.

## Database changes

Flyway core plus MySQL support owns migrations; Hibernate uses `ddl-auto=validate`. `baseline-on-migrate=true` and `baseline-version=1` support compatible V1 databases. Confirm the database identity and V1 schema before startup: automatic baselining is not a compatibility check. Back up first and use scoped migration permissions from [database setup](../database/README.md).

The `largeTextType` Flyway placeholder resolves to `CLOB` on default H2 and `LONGTEXT` on the MySQL profile. Keep the profile/placeholder consistent for a migrated database and never edit an applied migration.

V2 token expiry/reuse, session invalidation, provider failures, and absent-provider behavior are covered by the dated Java/API evidence in [verification](verification.md). The provider tests use an isolated local HTTP fixture, not a paid OpenAI request. Private file-mail tests do not establish real SMTP delivery.
