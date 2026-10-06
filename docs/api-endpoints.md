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
| PUT | `/api/profile` | Signed in | `{name,course,college,currentYear,careerId}`; saves profile and updates roadmap |
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
{"name":"Demo Student","course":"B.Sc. Computer Science","college":"Rizvi College","currentYear":"Third Year","careerId":1}
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

The current controllers return **200** for successful requests, including creates and deletes. Creates/updates return the entity; deletes and logout return `{message}`. The supplied Postman checks expect 200 for success and explicit error codes for negative examples.

## Postman workflow

Import [the collection](CareerForge.postman_collection.json), retain cookies, and run the Student folder after login. Collection variables hold the base URL, credentials, and record IDs. Skill/career catalog requests and the dashboard capture usable IDs; creation requests capture IDs for their temporary records. Review those IDs before deleting. The registration example creates a new account. Admin operations change data; use the temporary career/resource examples. Run Logout before the isolated unauthenticated request and sign in as a student before the forbidden admin request.
