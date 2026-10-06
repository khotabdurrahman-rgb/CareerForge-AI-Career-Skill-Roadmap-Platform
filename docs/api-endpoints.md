# REST API reference

Base URL: `http://localhost:8090`. Send JSON bodies with `Content-Type: application/json`. Paths below include `/api`.

## Authentication and conventions

Login and registration establish a server session (`JSESSIONID`). Keep cookies for subsequent requests. Successful sign-in invalidates any previous session. Logout invalidates the session. There is no bearer token. Session cookies are HttpOnly and SameSite Strict; inactivity timeout is 60 minutes. Mutating browser requests with a different Origin or `Sec-Fetch-Site: cross-site` are rejected. Postman uses its cookie jar without an unrelated Origin header.

Registration accepts `name`, `email`, and `password`; it creates a student, assigns an initial career, and generates a roadmap. Email is trimmed and normalized to lowercase. Passwords are BCrypt hashed and never returned, nor is `passwordHash` exposed. A registration cannot choose an administrator role.

IDs are database IDs, not list indices. `skillId` refers to the skill catalog; the `id` in `DELETE /api/skills/{id}` refers to a **user-skill record**. Roadmap, project, career, and resource mutations use their respective record IDs. Fetch actual IDs rather than relying on examples.

## Endpoint contract

| Method | Path | Access | Body / result |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | `{name,email,password}`; signs in new student |
| POST | `/api/auth/login` | Public | `{email,password}`; signs in account |
| POST | `/api/auth/logout` | Session lifecycle | No body; ends current session |
| GET | `/api/me` | Signed in | Current user, excluding password hash |
| GET | `/api/dashboard` | Signed in | Aggregated user, career, skills, careers, roadmap, projects, resources, and gap |
| GET | `/api/skills` | Signed in | Array of skill catalog entries `{id,name}` |
| POST | `/api/skills` | Signed in | `{skillId,level}`; adds skill or changes existing level |
| DELETE | `/api/skills/{id}` | Owner | Removes user-skill record; regenerates roadmap |
| GET | `/api/careers` | Public | Career catalog with required skills |
| GET | `/api/careers/{id}` | Public | One career with required skills |
| PUT | `/api/profile` | Signed in | `{name,course,college,currentYear,careerId,timezone?}`; saves profile and updates roadmap |
| PUT | `/api/roadmap/{id}` | Owner | `{status}`; updates selected-career step |
| GET | `/api/roadmap` | Signed in | Current selected-career roadmap; synchronizes generated steps |
| GET | `/api/projects` | Signed in | Current user's projects |
| POST | `/api/projects` | Signed in | `{name,description,technology,githubUrl,status}`; creates project |
| PUT | `/api/projects/{id}` | Owner | Same project body; updates owned project |
| DELETE | `/api/projects/{id}` | Owner | Removes project |
| GET | `/api/resources` | Signed in | Learning resource catalog |
| GET | `/api/admin/users` | Administrator | User list without password hashes |
| POST | `/api/admin/careers` | Administrator | `{name,description,skillIds}`; creates career |
| PUT | `/api/admin/careers/{id}` | Administrator | Same body; replaces career details and required skills |
| DELETE | `/api/admin/careers/{id}` | Administrator | Removes unused career; followed careers must first be reassigned |
| POST | `/api/admin/resources` | Administrator | `{skillId,title,url,type}`; creates learning resource |
| PUT | `/api/admin/resources/{id}` | Administrator | Same resource body; updates learning resource |
| DELETE | `/api/admin/resources/{id}` | Administrator | Removes learning resource |

No endpoint for creating arbitrary catalog skills is supplied. Student projects, roadmap, and resources are available through the dashboard as well as their listing endpoints.

The backend also exposes ownership-checked aliases:

| Method | Path | Body / result |
| --- | --- | --- |
| GET | `/api/users/{id}/skill-gap/{careerId}` | Gap for the current user and specified career |
| POST | `/api/users/{id}/skills` | Same body as `POST /api/skills` |
| GET | `/api/users/{id}/roadmap` | Current user's selected-career roadmap |
| GET | `/api/users/{id}/projects` | Current user's projects |
| POST | `/api/users/{id}/projects` | Same body as `POST /api/projects` |

For every alias, `{id}` must equal the signed-in user's ID; administrator role does not bypass this ownership rule.

## Request examples

Login:

```json
{"email":"student@careerforge.dev","password":"Career123!"}
```

Registration (use a new email each time):

```json
{"name":"Demo Student","email":"new.student@example.test","password":"Career123!"}
```

Profile:

```json
{"name":"Demo Student","course":"B.Sc. Computer Science","college":"Rizvi College","currentYear":"Third Year","careerId":1,"timezone":"Asia/Kolkata"}
```

User skill and roadmap update:

```json
{"skillId":1,"level":"Intermediate"}
```

```json
{"status":"COMPLETED"}
```

Project:

```json
{"name":"Student Portfolio","description":"A personal portfolio with project links.","technology":"HTML, CSS, JavaScript","githubUrl":"https://github.com/example/student-portfolio","status":"IN_PROGRESS"}
```

Career and resource:

```json
{"name":"Demo Career","description":"A temporary path for local API verification.","skillIds":[1,8]}
```

```json
{"skillId":1,"title":"Java learning portal","url":"https://dev.java/learn/","type":"DOCUMENTATION"}
```

The GitHub URL is an illustrative placeholder, not evidence of an existing repository.

## Validation

| Field | Accepted values / limits |
| --- | --- |
| Registration name | Nonblank; at most 100 characters |
| Registration email | Nonblank valid email; at most 255 characters; unique |
| Registration password | 8-72 characters, also at most 72 UTF-8 bytes |
| Profile name | Nonblank; at most 100 characters |
| Profile course / college | Optional; at most 255 characters each |
| Profile currentYear | Optional string; at most 50 characters |
| Profile careerId | Required; must identify an existing career |
| Profile timezone | Optional; at most 255 characters; valid Java ZoneId such as `Asia/Kolkata` |
| Skill level | Exactly `Beginner`, `Intermediate`, or `Advanced` |
| Roadmap status | Exactly `NOT_STARTED`, `IN_PROGRESS`, or `COMPLETED` |
| Project status | Exactly `IN_PROGRESS` or `COMPLETED` |
| Project name / description / technology | Nonblank name, max 150; optional description, max 2000; optional technology, max 255 |
| Project githubUrl | Optional absolute HTTP(S) URL with a host; max 1000 |
| Career name / description / skillIds | Nonblank name, max 150; nonblank description, max 2000; nonempty list of existing skill IDs |
| Resource title / url / type | Nonblank; maxima 255 / 1000 / 50; URL must be absolute HTTP(S) with a host |

Resource `type` is a nonblank label rather than a fixed enum. Skill level is recorded for display; readiness counts the presence of required skills, without weighting proficiency.

## Dashboard and roadmap behavior

The dashboard object contains `user`, `career`, `skills`, `careers`, `roadmap`, `projects`, `resources`, and `gap`. `gap` contains `completed`, `missing`, and numeric `readiness`. `skills` here contains user-skill records with nested `skill`; career objects contain their required `skills`. Roadmap steps contain `id`, `careerId`, nested `skill` (nullable), `title`, `status`, and `position`. Projects expose `githubUrl` using camelCase.

Readiness is `(completed required skills / all required skills) * 100`, rounded to one decimal. An empty requirement list produces zero. Three recorded skills out of eight requirements yield 37.5% regardless of their level.

Roadmaps are synchronized when skills/profile change and when the dashboard is read. Completing a skill-linked step adds a missing user skill at `Beginner`. Returning it to an unfinished state removes that user skill. A capstone step has no linked skill and does not change readiness. Steps from another selected career cannot be updated until that career is selected. Career requirement changes remove obsolete steps when the roadmap is next synchronized.

## Errors

Handled errors use a JSON `message`, for example:

```json
{"message":"Please sign in to continue"}
```

| Status | Meaning |
| --- | --- |
| 400 | Invalid input/format/status/URL, selected-career mismatch, or deletion of a followed career |
| 401 | Missing/expired session or incorrect credentials |
| 403 | Administrator access required, another user's record, or rejected cross-site request |
| 404 | Referenced record not found |
| 409 | Duplicate email or database uniqueness/reference conflict |

The current controllers return **200** for successful requests, including creates and deletes. V1 creates/updates generally return entities; V2 learning/extras routes use safe DTOs. Deletes and logout return `{message}`. PDF export returns binary PDF. The supplied Postman examples are not executed test evidence.

## V2 account recovery and verification

| Method | Path | Access | Body / result |
| --- | --- | --- | --- |
| GET | `/api/auth/config` | Public | `{emailAvailable,localEmail,message}`; no secrets |
| POST | `/api/auth/forgot-password` | Public | `{email}`; generic message whether eligible or not |
| POST | `/api/auth/verification` | Public | `{email}`; generic verification-email request response |
| POST | `/api/auth/reset-password` | Public | `{token,password}`; resets password and invalidates old sessions |
| POST | `/api/auth/verify-email` | Public | `{token}`; marks email verified |

Recovery/verification requests are limited to five per client IP per 15 minutes; token submissions to 15 per IP per 15 minutes. Token strings are nonblank, max 200. Reset passwords have the registration length/UTF-8 limits. Reset links expire after 30 minutes; verification links after 24 hours. Tokens are single-use, purpose-bound, and hashed in storage. Invalid/expired/used links return 400. New requests invalidate earlier unused tokens of that purpose. With SMTP absent, requests remain generic and no delivery is claimed. `smtp-file` writes private `.eml` messages under `backend/data/mail` from the backend working directory. Obtain test tokens there; never capture raw tokens in normal API/UI responses or screenshots. Reset requires signing in again. `emailVerified` and `timezone` are public user fields; `sessionVersion` and password hashes are private.

## V2 mentor

| Method | Path | Access | Body / result |
| --- | --- | --- | --- |
| GET | `/api/mentor` | Signed in | `{available,message,messages,dailyLimit,remaining}` |
| POST | `/api/mentor` | Signed in | `{message}`; returns updated overview and history |
| DELETE | `/api/mentor/history` | Signed in | Clears this user's history; usage quota remains |

Messages are nonblank, max 1000 characters. Default limit is 20 reserved requests per UTC day; attempts that fail at the provider still consume reserved quota. A second in-flight request for the same user returns 409, exhausted quota 429, missing API key 503, provider/unusable output errors 502, and timeout 504. Only successful answers persist user/assistant messages. Overview returns the most recent 60 messages chronologically; the provider receives the last 12 plus the question and learning context. OpenAI requests use `store:false`; local database history persists separately. Model defaults to `gpt-4.1-mini`. The `careerforge.ai.url` property can be overridden for isolated REST transport tests; use the official endpoint in normal operation. See [configuration](security-configuration.md) and the [Responses reference](https://developers.openai.com/api/reference/python/resources/responses/methods/create).

## V2 assessments and weekly planner

All routes below require a session. Full backend-owned behavior is in [the learning contract](v2-learning-contract.md).

| Method | Path | Body / result |
| --- | --- | --- |
| GET | `/api/assessments` | `{skills,attempts}` with best scores and attempt history |
| POST | `/api/assessments/{skillId}/start` | No body; `{id,skillId,skillName,expiresAt,questions:[{id,prompt,options}]}` |
| POST | `/api/assessments/sessions/{sessionId}/submit` | `{answers:[{questionId,optionIndex}]}`; `{attempt,feedback}` |
| GET | `/api/planner?week=YYYY-MM-DD` | `{weekStart,weekEnd,timezone,goals,tasks,overdue,plannedMinutes,completedMinutes}` |
| POST / PUT | `/api/planner/goals` / `/api/planner/goals/{id}` | `{title,weekStart,targetMinutes}` |
| DELETE | `/api/planner/goals/{id}` | Removes owned goal |
| POST / PUT | `/api/planner/tasks` / `/api/planner/tasks/{id}` | `{title,skillId?,roadmapStepId?,dueDate,estimatedMinutes,status,weekStart}` |
| DELETE | `/api/planner/tasks/{id}` | Removes owned task |

Answer IDs must match the started session exactly once; option indices are zero-based. Scoring is server-side against the stored snapshot; start never exposes answer keys. Sessions expire after 60 minutes (410); resubmission returns 409; missing/other-user sessions return 404. Assessments do not change self-reported skills or roadmap readiness.

Weeks normalize to Monday-Sunday and default to the user's timezone. Goal target minutes are 1-10080; task minutes 1-1440; titles max 255. Due dates must fall within the normalized week. Task statuses are `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`. Owned goal/task lookup failures return 404. Optional roadmap links must belong to the user and active career; skill links must match. Completing a linked task completes its roadmap step with the existing skill side effects; reopening/deleting a task does not undo them. Overdue tasks include all incomplete tasks before today's local date, across weeks.

## V2 resume, comparison, recommendations, and progress

All routes require a session. Full backend-owned behavior is in [the extras contract](v2-extras-contract.md).

| Method | Path | Body / result |
| --- | --- | --- |
| GET | `/api/resume` | `{profile,user,skills,projects}` |
| PUT | `/api/resume` | Complete resume-fields body; returns current bundle |
| GET | `/api/resume/pdf` | `application/pdf`, attachment `careerforge-resume.pdf`, `Cache-Control: no-store` |
| GET | `/api/careers/compare?left={id}&right={id}` | `{left,right,sharedSkills,estimateExplanation}`; selected career unchanged |
| GET | `/api/recommendations` | Curated recommendation DTOs with saved/project state |
| POST / DELETE | `/api/recommendations/{id}/save` | Save/unsave recommendation; returns recommendation |
| POST | `/api/recommendations/{id}/portfolio` | Add recommendation to portfolio; returns project DTO |
| GET | `/api/progress` | `{events,trend,streak,currentTimezone,streakDefinition}` |

Resume fields: `headline` (max 200), `summary` (4000), `phone` (60), `location` (200), `website` (1000), `education`, `experience`, `achievements` (4000 each). Include required booleans `includeSkills`, `includeProjects`, `includeEducation`, `includeExperience`, `includeAchievements`. PUT replaces the editable fields; user/skills/projects are sourced from current records. PDF uses PDFBox 3.0.4. Comparison estimates are guidance, not measured course duration or employment probability. Progress shows up to the latest 500 events; streak is consecutive local activity dates ending today or yesterday. Readiness snapshots reflect declared skill coverage, not assessment scores. Historical scalar references are retained when linked roadmap/project/career records disappear.

Resume JSON is limited to 20000 encoded characters; a nonblank website must be absolute HTTP(S), have a host, and contain no credentials. PDF uses bundled Noto Sans for supported glyphs; unsupported scripts/glyphs use fallback, and full complex-script shaping is not claimed. Comparison uses 20 estimated hours per missing skill, excluding capstone work. Recommendation listing filters the selected career and declared prerequisites; save/unsave is idempotent. Adding to portfolio returns the same previously created project; if it was deleted, the durable tombstone returns 409 rather than creating another project.

PDF clients should send `Accept: application/pdf, application/json` (or `*/*`) so successful PDF responses and JSON errors are accepted. A JSON-only Accept header can produce 406.

## Authentication limits and monitoring

POST login and registration use separate client-IP budgets before parsing/validation (defaults: 20/15 minutes and 10/hour). A blocked request returns JSON 429 with `Retry-After` seconds; logout or changing email does not clear it. Recovery and token endpoints retain their existing limits. Limits are per process, not cluster-wide.

Public GET `/actuator/health/readiness` returns status-only 200/UP or 503/DOWN and checks database availability. GET `/actuator/health/liveness` checks application liveness without optional SMTP/AI calls. `/actuator/health` also lists the health group names. Other management endpoints and details are not exposed.

## Postman workflow

Import [the collection](CareerForge.postman_collection.json), retain cookies, and run the Student folder after login. Collection variables hold the base URL, credentials, and record IDs. Skill/career catalog requests and the dashboard capture usable IDs; creation requests capture IDs for their temporary records. Review those IDs before deleting. The registration example creates a new account. Admin operations change data; use the temporary career/resource examples. Run Logout before the isolated unauthenticated request and sign in as a student before the forbidden admin request.
