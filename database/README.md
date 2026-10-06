# Database setup

H2 is the default persistent local demo database. These SQL files target **MySQL**, not H2. JPA maps the same eight entities: `users`, `skills`, `user_skills`, `careers`, `career_skills`, `roadmap_steps`, `projects`, and `resources`. There is no separate `roadmaps` table.

## Import

Using MySQL Workbench, open and execute `schema.sql`. For an optional illustrative catalog, execute `sample-data.sql` on the fresh schema before application startup. In the MySQL client, the equivalent commands are:

```sql
SOURCE C:/path/to/CareerForge/database/schema.sql;
SOURCE C:/path/to/CareerForge/database/sample-data.sql;
```

Replace the path with the actual workspace. The schema creates `careerforge` if absent and never drops tables. `CREATE TABLE IF NOT EXISTS` does not repair an incompatible existing schema; inspect it before importing into an existing installation. Sample data uses explicit IDs and must be imported once. Import without the client's continue-on-error option; roll back a failed seed transaction before retrying.

Demo users are initialized by the backend with BCrypt-hashed passwords when demo seeding is enabled and the users table is empty. Sample SQL intentionally contains no plaintext passwords or implementation-specific password hashes. Allow the application initializer to create `student@careerforge.dev` and `admin@careerforge.dev` with `Career123!`. The sample catalog matches the Java seed's 18 skills, four careers, and 18 resources on a fresh import; query actual IDs before API mutations. Java initialization adds the student's Java, HTML, CSS, Git, and MySQL skills (5/8 requirements, 62.5% readiness), roadmap, and three projects. Set `SEED_DEMO=false` to suppress demo-user creation; catalog initialization still occurs when its tables are empty.

## Application account

Run as a MySQL administrator, replacing the password before execution:

```sql
CREATE USER 'careerforge_app'@'localhost' IDENTIFIED BY 'replace-with-local-password';
GRANT SELECT, INSERT, UPDATE, DELETE
    ON careerforge.* TO 'careerforge_app'@'localhost';
```

The MySQL profile uses `ddl-auto=validate`: import the schema first with an administrator account; the application needs only data access. Adjust the host if the database and application are on different machines.

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/careerforge'
$env:DB_USERNAME = 'careerforge_app'
$env:DB_PASSWORD = 'replace-with-local-password'
.\scripts\start.ps1 -Profile mysql
```

## Optional XAMPP / MariaDB environment

**2026-10-06:** the schema imported successfully using XAMPP's MariaDB 10.4 with isolated data at `.tools/mysql-data` and database port **3307**. Live API checks passed **17 assertions** using the MySQL application profile on port **8091**. H2 on port **8090** also passed 17 assertions. Oracle MySQL was not installed or tested; these results establish MariaDB compatibility for the exercised workflows.

For an already running local MariaDB server, set `DB_URL` to `jdbc:mysql://localhost:3307/careerforge`, set credentials for that server, and optionally set `$env:PORT = '8091'` before `.\scripts\start.ps1 -Profile mysql`. This guide does not start or initialize a XAMPP service. Port 3306 and application port 8090 remain the normal examples. Remove the override with `Remove-Item Env:PORT` before returning to the default H2 demo. Do not point multiple database processes at the same data directory.

## Constraints

| Constraint | Purpose |
| --- | --- |
| Unique user email | Prevent duplicate identities |
| Unique skill name | Keep one catalog entry per skill |
| Unique `(user_id, skill_id)` | Keep one level per user skill |
| Composite career-skill primary key | Prevent duplicate required skills |
| Unique roadmap `(user_id, career_id, title)` | Prevent duplicate generated steps |
| Foreign keys | Prevent orphan references |
| User child cascades | Remove owned skills, roadmap steps, and projects on user deletion |
| Nullable user career with `SET NULL` | Retain users when a career is deleted |
| Roadmap index `(user_id, position)` | Support ordered per-user roadmap lookup |

Statuses and levels are stored as strings so they can match application values without MySQL-specific enum mappings. `current_year` is a string (for example, `Third Year`). A roadmap step may have a null `skill_id` for the portfolio capstone. Check the API reference for supported values. Neither readiness nor dashboard totals are stored; they are derived from relationships. The API blocks deletion of a career followed by students, even though direct SQL has a `SET NULL` fallback. Back up persistent data before schema changes. No destructive reset script is supplied.
