# Changelog

## Public-site hardening - 2026-10-07

- Login/registration IP budgets before parsing, separate operation buckets, and 429 `Retry-After` responses.
- Bounded limiter storage, per-bucket expiry, and atomic concurrent admission checks.
- Minimal Actuator health probes with database-aware readiness; private management endpoints remain unexposed.
- Internal rate-limit counters without email/IP labels, restrained public logging, and automatic Render email-link URL fallback.
- Render launch checklist, public-mode browser QA, and seven additional regression tests.

## V2 - 2026-10-06

- Flyway versioned migrations and Hibernate schema validation. Compatible existing V1 databases are baselined at version 1 before V2; records are retained.
- User timezone, email verification, and session version for invalidating old sessions after password reset.
- Account recovery/verification through optional SMTP or private local mail files.
- Optional OpenAI Responses API mentor, local history, and daily usage tracking.
- Skill assessments, weekly planning, progress history, saved recommendations, resume editing, and PDF export with PDFBox 3.0.4.
- Expanded configuration, API/Postman, setup, college report, and verification documentation.

Verification includes 41 passing Java tests and 158 live API checks on each of H2 and the MySQL/MariaDB profile. Both migrated databases start with Hibernate validation and retain V1 data. See [verification](verification.md) for browser evidence and untested external services. Migration SQL is frozen after successful execution; future schema changes require a later version.

## V1

Session authentication, profile/career selection, deterministic skill-gap comparison, synchronized roadmaps, projects, and administrator catalog maintenance.
