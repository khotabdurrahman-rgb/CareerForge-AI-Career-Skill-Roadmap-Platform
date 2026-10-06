# Database setup and migrations

H2 in MySQL mode is the persistent default at `backend/data/careerforge` when launched from the backend working directory. The `mysql` profile connects to an existing MySQL database. Flyway owns schema changes; Hibernate uses `ddl-auto=validate`.

## Fresh MySQL installation

Create the database only with an administrator:

```sql
CREATE DATABASE IF NOT EXISTS careerforge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'careerforge_app'@'localhost' IDENTIFIED BY 'replace-with-local-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON careerforge.* TO 'careerforge_app'@'localhost';
```

Use the appropriate existing account instead of repeating `CREATE USER`. Flyway needs schema-change permissions and access to `flyway_schema_history`; old data-only grants are insufficient. Scope grants to this database and adapt the host.

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/careerforge'
$env:DB_USERNAME = 'careerforge_app'
$env:DB_PASSWORD = 'replace-with-local-password'
.\scripts\start.ps1 -Profile mysql
```

An empty database runs V1 then V2 automatically. Do not import a full V2 schema before startup. Optional `sample-data.sql` is for an empty catalog after migration, with `SEED_DEMO=false`; avoid explicit-ID collisions in a seeded database. It contains no account passwords. Application catalog initialization still runs when catalog tables are empty, so manual seed imports are generally unnecessary.

## Upgrade existing V1 data

1. Stop all application instances and back up the database. Copy H2 files only while stopped.
2. Confirm the tables match V1. Automatic baselining is a marker, not schema repair or a compatibility check.
3. Verify the database URL and grant migration permissions.
4. Start V2. A compatible nonempty schema without Flyway history receives a version-1 baseline, skips V1, then runs V2.
5. Inspect `flyway_schema_history`, confirm Hibernate validation, and compare existing IDs/records with the backup. Existing users receive `timezone='Asia/Kolkata'`, `email_verified=false`, and `session_version=0`.
6. Restart and verify no migration runs again. Record actual evidence in [verification](../docs/verification.md).

V2 does not drop V1 tables or reset records. MySQL DDL can commit before a migration fails: inspect partial changes and restore the backup when necessary. Never blindly rerun the reference SQL, hide errors with repair, or edit an applied migration. Future changes need a new migration version.

## SQL file roles

| File | Purpose |
| --- | --- |
| `schema.sql` | Historical manual V1 MySQL bootstrap with database/engine directives |
| `sample-data.sql` | Optional one-time catalog examples with explicit IDs |
| `../backend/src/main/resources/db/migration/V1__initial_schema.sql` | Executable V1 for H2 MySQL mode and MySQL |
| `../backend/src/main/resources/db/migration/V2__careerforge_v2.sql` | Executable additive V2 |
| `migrations/V2__careerforge_v2.sql` | Reference copy only; not another Flyway location or manual bootstrap |

A previously imported `schema.sql` is acceptable V1: startup baselines it then applies V2. Keep it at V1 to avoid V2 running twice. Do not import historical MySQL scripts into H2.

V2 uses the Flyway placeholder `${largeTextType}` for `quiz_sessions.question_set` and `resume_profiles.fields_json`. The default H2 configuration substitutes `CLOB`; the MySQL profile substitutes `LONGTEXT`. The reference copy is byte-identical SQL, but cannot be imported manually until both placeholder occurrences are substituted for the target database. Normal application startup performs substitution automatically. Never manually apply that copy to an already migrated database. After the first live migration/checksum is recorded, freeze the SQL and placeholder configuration for that database; later changes require a new migration.

## Model and integrity

V1 has users, skills, user skills, careers, career skills, roadmap steps, projects, and resources. V2 adds account tokens, mentor messages/usage, assessment attempts/quiz sessions, planner goals/tasks, progress events, saved recommendations, and resume profiles. Exact columns follow entity annotations and learning/extras contracts.

V1 uniqueness covers email, skill name, user/skill, career/skill, and roadmap user/career/title. V2 adds unique token hashes, mentor user/date, assessment session, resume user, and recommendation user/identifier. Every V2 user reference has a foreign key, including scalar `user_id` fields in progress, saved recommendations, and resumes. Historic career/project/roadmap scalar references deliberately have no foreign key. Roadmap skill is nullable for capstones. Readiness remains derived.

## Compatibility evidence

On 2026-10-06, isolated H2/Flyway fresh, baseline-upgrade, restart/no-op, and checksum validation checks succeeded. The parent separately reported a successful live original-H2-data upgrade, retained records, Hibernate validation with TIMESTAMP/UTC Instant mapping, and the 2.0.0 JAR running on port 8090. Migration SQL is frozen. Earlier MariaDB results are historical V1 evidence; V2 MySQL/MariaDB and Oracle MySQL execution remain pending. An existing MariaDB server on port 3307 uses `jdbc:mysql://localhost:3307/careerforge`; this guide does not start a database service.
