# CareerForge V2 Extras Contract

Scope: `com.careerforge.v2.extras`, `recommendations.json`, `resources/fonts/**` (explicitly authorized expansion), and this document. Parent owns the PDFBox 3.0.4 dependency, `User.timezone` (default `Asia/Kolkata`), migrations, V1 mutation hooks, and learning/assessment features.

All routes require `AuthService.current(request)`. Ownership is derived exclusively from the authenticated session; request payloads and query strings never select a user. Responses are explicit DTOs, materialized inside transactions, with no password hash, role, session version, verification state, entity owner relationships, or Hibernate internals. Existing CSRF/origin rules and error responses apply. Unauthorized requests return 401; invalid input 400; unknown careers/recommendations 404.

## Resume

`GET /api/resume` and `PUT /api/resume` return exactly:

```json
{
  "profile": {
    "headline": "", "summary": "", "phone": "", "location": "", "website": "",
    "education": "", "experience": "", "achievements": "",
    "includeSkills": true, "includeProjects": true, "includeEducation": true,
    "includeExperience": true, "includeAchievements": true
  },
  "user": {"name": "Student", "email": "student@example.com", "course": "", "college": "", "currentYear": ""},
  "skills": [{"id": 1, "skill": {"id": 1, "name": "Java"}, "level": "Beginner"}],
  "projects": [{"id": 1, "name": "Example", "description": "", "technology": "Java", "githubUrl": "", "status": "IN_PROGRESS"}]
}
```

PUT accepts the fields directly, without a `profile` wrapper. It replaces all editable fields. All five boolean toggles are required and must be non-null. Text fields may be null or empty. Limits: headline/location 200, summary/education/experience/achievements 4000 each, phone 60, website 1000; encoded JSON must fit 20000 characters. Nonblank website must be an absolute HTTP(S) URL with a host and no credentials. PUT never changes V1 user, skill, or project data. Initial GET builds an in-memory default with education from course/college/year and all toggles true; GET does not persist it. Null values in the user DTO mirror V1 fields.

`GET /api/resume/pdf` downloads `careerforge-resume.pdf` with `application/pdf` and `Cache-Control: no-store`. PDFBox 3.0.4 creates selectable text in a single A4 column with measured wrapping, word/URL splitting, and pagination. Name, headline, contact details, and nonblank summary always appear; each optional section respects its toggle. Skills show names without proficiency claims; all current V1 projects are included when enabled. Empty sections are omitted. PDF metadata contains only resume title and sanitized name.

Fonts: bundled Noto Sans Regular/Bold are embedded as subsetted `PDType0Font` fonts, preserving selectable Unicode text for glyphs covered by these fonts (including accented Latin names). These are unmodified assets from [the Noto font repository](https://github.com/notofonts/noto-fonts/tree/main/hinted/ttf/NotoSans), distributed under [SIL Open Font License 1.1](https://github.com/notofonts/noto-fonts/blob/main/LICENSE); the copyright notice and full license ship as `resources/fonts/OFL.txt`. No runtime network font fetch occurs. This is not full Unicode/script support: absent glyphs such as many emoji, CJK, and Indic characters become `?`; PDFBox does not supply complex-script shaping. Control and format characters are removed. If font resources are absent, Standard 14 Helvetica falls back to NFKD accent transliteration and safe supported glyphs. JSON always retains original Unicode.

## Comparison

`GET /api/careers/compare?left=1&right=2`:

```text
{left:{career:{id,name,description,skills:[{id,name}]},gap:{completed:[{id,name}],missing:[{id,name}],readiness},estimatedHours},
 right:{career:{id,name,description,skills:[{id,name}]},gap:{completed:[{id,name}],missing:[{id,name}],readiness},estimatedHours},
 sharedSkills:[{id,name}],estimateExplanation}
```

Both IDs must be positive and exist; comparing a career with itself is valid. Readiness and completed/missing membership reuse `CareerService.gap`, including its one-decimal rounding. Shared skills are the intersection of all required skills, not only missing skills. Estimate is 20 hours per missing skill, excluding capstone work; it is a planning heuristic, not a completion guarantee. Explanation explicitly distinguishes declared learning coverage from assessed proficiency.

## Recommendations

`GET /api/recommendations` returns an array:

```text
[{id:string,title,description,difficulty,technologies:[string],prerequisites:[string],
  milestones:[string],careerIds:[long],saved:boolean,addedProjectId:long|null}]
```

The JSON catalog contains two projects per seeded career, one beginner and one intermediate. It maps stable career names to live database IDs rather than assuming seeded numeric IDs. Newly administered careers need curated catalog entries; renamed careers need matching catalog names. IDs are stable lowercase hyphenated slugs. Beginner projects have no prerequisites so a new learner always has an eligible starting project for a seeded career. Listing filters to the selected career and requires every prerequisite in the user's declared skill names, compared case-insensitively. Eligible projects rank by number of already-known technologies descending, then Beginner before Intermediate, then slug ascending. Empty eligible sets return `[]`. Read-only listing does not write bookmarks, projects, roadmap steps, or progress events.

`POST /api/recommendations/{id}/save` returns the same recommendation object with `saved:true`. `DELETE /api/recommendations/{id}/save` returns it with `saved:false`. These operations are idempotent; unbookmarking preserves `addedProjectId`. Both may act on any existing catalog slug regardless of current listing eligibility, allowing existing bookmarks to remain manageable after a career change.

`POST /api/recommendations/{id}/portfolio` returns `{id,name,description,technology,githubUrl,status}` for the V1 project. First request calls `CareerService.saveProject(user,null,ProjectInput)` with the curated title/description, joined technologies, blank GitHub URL, and `IN_PROGRESS`. Subsequent calls return the same user's project without updating user edits or calling `saveProject` again. Bookmark state is independent of portfolio state. If the project was subsequently deleted, return 409 with a message rather than creating a second project. No project-ID foreign key is used because the ID is a durable idempotency tombstone. Save/unbookmark/add lock the user's database row with `PESSIMISTIC_WRITE`; the unique constraint supplies an additional invariant. Project creation and idempotency state commit or roll back together. V1 `saveProject` owns the corresponding activity hook; extras does not duplicate its progress event.

## Activity And History

`ProgressService` is an `@Service` with `@Transactional public void record(User user,String type,String title,double readiness)`. Dependencies are only its event repository, `UserRepository`, and `EntityManager`; it does not inject `CareerService`, avoiding a constructor dependency cycle. The write method only saves through `ProgressEventRepository`; `UserRepository` is used by the history read method. Calls join the caller transaction (`REQUIRED`); a rolled-back mutation also rolls back its event. The supplied user is unproxied before reading public fields, and its owner ID comes from the persistence identifier. Callers supply the authenticated/managed user, never a user constructed from request IDs. Type must be nonblank and at most 64 characters; title nonblank and at most 255; readiness must be finite and in [0,100]. Events persist the supplied user's current career ID and server `Instant.now()`.

Parent and learning-agent integration agreement: call `record` only after successful meaningful mutations, in the existing write transaction. Never hook dashboard, roadmap generation on GET, any other GET, login, or background reads. Do not record both a facade and its delegated mutation. V1 project creation, including recommendation portfolio creation, should have one `saveProject` hook. Record skill/roadmap/profile/project mutations with readiness from `CareerService.gap(user,user.careerId)`. Learning/capstone completion can use `LEARNING_COMPLETED` / `CAPSTONE_COMPLETED` with a title explicitly describing learning. Assessment outcomes can use `ASSESSMENT_COMPLETED` with a title explicitly describing assessed results; the readiness argument still means V1 declared-learning readiness unless the parent changes this API contract. Capstone learning never implicitly marks an assessed skill or alters the skill-gap score. Avoid writing duplicate events for unchanged/idempotent operations. Event types are extensible labels, not an enum whitelist.

`GET /api/progress` returns:

```text
{events:[{id,type,title,readiness,occurredAt,careerId}],
 trend:[{date,readiness,careerId}],streak,currentTimezone,streakDefinition}
```

At most the latest 500 events are read per owner, sorted `occurredAt DESC,id DESC`. `occurredAt` is an ISO-8601 UTC instant. Trend uses the last event per (local date, career ID), preserving different career series rather than silently mixing them; points sort by date ascending, then career ID. Dates are `YYYY-MM-DD` in the user's `ZoneId`. Streak counts distinct consecutive activity dates ending today or yesterday in that timezone; otherwise zero. Multiple events in a day count once. All recorded activity types count, including capstone learning and assessed results. Empty history gives empty arrays and streak 0. Both trend and streak are bounded by the 500-event window; histories outside this window are not inferred. Invalid/null legacy timezone falls back to `Asia/Kolkata`; `currentTimezone` reports the effective zone. `streakDefinition` explains the window, declared versus assessed readiness, and capstone behavior. GET does not generate a baseline event.

## Migration DDL

Apply these tables once through the parent's migration sequence, after `users` exists. Table/column names are explicitly mapped. The application persists numeric owner IDs, with database FKs enforcing ownership existence. User deletion cascades extras records. Career IDs are historical snapshots with no FK so deleting or changing a career does not erase activity. Project tombstones intentionally have no FK. Existing installations should reconcile tables previously created by Hibernate rather than blindly re-running CREATE.

MySQL 8 (InnoDB, utf8mb4; event datetime values stored as UTC):

```sql
CREATE TABLE resume_profiles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  fields_json LONGTEXT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_resume_user UNIQUE (user_id),
  CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE saved_recommendations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  recommendation_id VARCHAR(100) NOT NULL,
  saved BOOLEAN NOT NULL,
  added_project_id BIGINT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_recommendation_user UNIQUE (user_id, recommendation_id),
  CONSTRAINT fk_saved_recommendation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE progress_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  readiness DOUBLE NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  career_id BIGINT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT ck_progress_readiness CHECK (readiness >= 0 AND readiness <= 100),
  INDEX ix_progress_user_time (user_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

H2 2.x (including MySQL compatibility mode):

```sql
CREATE TABLE resume_profiles (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL,
  fields_json CLOB NOT NULL,
  CONSTRAINT uk_resume_user UNIQUE (user_id),
  CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE saved_recommendations (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL,
  recommendation_id VARCHAR(100) NOT NULL,
  saved BOOLEAN NOT NULL,
  added_project_id BIGINT,
  CONSTRAINT uk_recommendation_user UNIQUE (user_id, recommendation_id),
  CONSTRAINT fk_saved_recommendation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE progress_events (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  readiness DOUBLE PRECISION NOT NULL,
  occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  career_id BIGINT,
  CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT ck_progress_readiness CHECK (readiness >= 0 AND readiness <= 100)
);
CREATE INDEX ix_progress_user_time ON progress_events(user_id, occurred_at, id);
```
