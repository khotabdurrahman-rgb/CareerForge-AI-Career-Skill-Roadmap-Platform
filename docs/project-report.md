# CareerForge: Student Career Planning and Skill Gap Analysis

College mini-project report. This source is ready for institutional details and observed results to be filled in before submission.

| Submission detail | Value |
| --- | --- |
| Institution / department | [Enter official institution and department] |
| Programme / semester | [Enter programme and semester] |
| Student names / roll numbers | [Enter team members and roll numbers] |
| Project guide | [Enter guide's name and designation] |
| Academic year | [Enter academic year] |
| Submission date | [Enter date] |

## 1. Abstract

CareerForge is a Java web application that helps students connect their current skills with a chosen career path. Students maintain profiles, record proficiency levels, identify missing skills, track an ordered learning roadmap, and collect portfolio projects. Administrators maintain career requirements and learning resources. The system uses Spring Boot, Maven, Java 17, Spring Data JPA, session authentication, and BCrypt password hashing. A persistent H2 database enables a local demonstration without installing a database server; a MySQL profile supports an external relational database. Skill recommendations are deterministic set comparisons rather than AI predictions. V2 adds server-scored assessments, weekly planning, activity history, career comparison, saved recommendations, resume/PDF export, account recovery/email verification, and an optional OpenAI mentor. Flyway migrations preserve compatible V1 data and Hibernate validates the result. The project demonstrates authentication, relational modelling, REST APIs, business logic, and student workflows.

## 2. Problem statement

Students often find learning material and career requirements in separate places. A list of courses does not show which skills a student already has, which are missing, or how progress relates to a selected goal. Manual tracking can become inconsistent as skills and career choices change. CareerForge consolidates these records and gives students a transparent view of the next learning actions.

## 3. Objectives

- Provide authenticated student and administrator workflows.
- Maintain student profile, skills, career choice, roadmap, and portfolio projects.
- Compare recorded skills with career requirements and show a readiness percentage.
- Keep roadmap progress synchronized with learned skills.
- Associate learning resources with skills.
- Demonstrate a normalized database and reusable REST interface.

## 4. Scope

Students register or sign in, update their profiles, choose a career, add/remove skills, change roadmap statuses, and add/delete projects. Administrators view users, create/update/delete careers, and create/delete resources. Skills form a seeded catalog; there is no skill-catalog CRUD endpoint in the specified interface. The dashboard aggregates related records for the application UI.

V2 includes curated multiple-choice assessments, weekly plans using the user's timezone, local activity history, career comparison, project recommendations, resume authoring/PDF export, recovery/verification emails, and an optional OpenAI mentor. Assessments score quiz answers separately from self-reported skills; they do not certify proficiency or change declared readiness. Job placement, real-time job feeds, and resume parsing remain outside scope. Readiness measures configured skill coverage, not employment probability.

## 5. Existing and proposed system

| Dimension | Manual/disconnected approach | CareerForge approach |
| --- | --- | --- |
| Student records | Notes and separate documents | Profile and related database records |
| Career requirements | Repeated manual research | Administrator-maintained requirements |
| Skill gaps | Manually compare lists | Compare distinct skill identifiers |
| Progress | Separate checklists | Roadmap linked to recorded skills |
| Portfolio | Scattered links | Projects associated with a student |
| Resources | General bookmarks | Resources associated with skills |

Core workflows require no paid external service. The optional OpenAI mentor requires configured provider access; SMTP requires an email service, while the private file-mail profile supports local testing. Java and Maven support reproducible builds, while H2 reduces demonstration setup. MySQL allows the same relational design to be explained and evaluated independently.

## 6. Requirements

| Category | Requirement |
| --- | --- |
| Development runtime | JDK 17 and Maven; workspace Maven fallback supported |
| Application | Spring Boot backend rooted at `backend/pom.xml`, default port 8090; Flyway core/MySQL, PDFBox 3.0.4 |
| Data | Default H2 file database; optional MySQL profile |
| Client | Modern browser; Postman for manual API checks |
| Functional | V1 workflows plus assessments, planner, activity, comparison, recommendations, resume/PDF, recovery/verification, optional mentor |
| Nonfunctional | Persistent data, clear validation, ownership checks, hashed passwords, reproducible setup |

No minimum hardware performance or load capacity is claimed without measurement. See [the 20-step setup guide](setup-20-steps.md) for the procedure.

## 7. Architecture

```text
Browser application / Postman
              |
        HTTP + session cookie
              |
     Spring Boot REST controllers
              |
 Authentication and career services
              |
     Spring Data JPA repositories
              |
  H2 file database / MySQL profile
```

Controllers receive and validate requests. Services implement authentication, skill comparison, ownership checks, and roadmap synchronization. Repositories access entity records. JPA maps entities to relational tables. The application remains a single local service for a straightforward college demonstration.

## 8. Modules and use cases

| Module | Actor | Main behavior |
| --- | --- | --- |
| Authentication | Visitor / user | Register, log in, inspect current session, log out |
| Profile | Student | Save name, course, college, year, and career goal |
| Skills | Student | Record proficiency; update existing skill; remove owned record |
| Career analysis | Student | Compare learned and required skills; show missing skills |
| Roadmap | Student | Follow ordered steps and update completion |
| Portfolio | Student | Create and remove owned projects |
| Administration | Administrator | View users; maintain careers and resources |
| Assessments | Student | Start private snapshot quiz, submit once, review score/history |
| Weekly planner | Student | Maintain goals/tasks and local-week totals/overdue work |
| Progress and comparison | Student | Inspect activity/streak and compare two careers without changing selection |
| Recommendations | Student | Save curated projects and add once to portfolio |
| Resume | Student | Edit fields/toggles and download a paginated PDF |
| Recovery / verification | Visitor / user | Consume single-use links from SMTP or private local mail |
| Mentor | Student | Optional server-side OpenAI advice with local history and daily quota |

Student workflow: sign in -> complete profile -> choose career -> record skills -> inspect gap -> follow roadmap -> record project. Administrator workflow: sign in -> inspect users -> maintain requirements/resources -> sign out.

## 9. Database design

| Table | Main columns | Relationship |
| --- | --- | --- |
| `users` | id, name, email, password_hash, course, college, current_year, career_id, role | Optional selected career |
| `skills` | id, name | Shared catalog |
| `user_skills` | id, user_id, skill_id, level | User-to-skill association |
| `careers` | id, name, description | Career catalog |
| `career_skills` | career_id, skill_id | Career-to-skill association |
| `roadmap_steps` | id, user_id, career_id, skill_id, title, status, position | Ordered user/career plan; skill nullable for capstone |
| `projects` | id, user_id, name, description, technology, github_url, status | Student portfolio |
| `resources` | id, skill_id, title, url, type | Skill-linked learning resource |

Users have many user skills, roadmap steps, and projects. Careers and skills have a many-to-many relationship through `career_skills`. Each resource belongs to one skill. A roadmap belongs to a student and career and may reference a skill.

The model avoids comma-separated skill identifiers by using association tables. Unique emails, unique skill names, unique user-skill pairs, and unique roadmap titles per user/career prevent duplicate records. Foreign keys enforce reference integrity. Profile year is text, such as `Third Year`. Readiness is calculated instead of stored, reducing stale derived values. The historical V1 design is supplied in [schema.sql](../database/schema.sql). Executable V1/V2 SQL is in `backend/src/main/resources/db/migration`; the database guide explains fresh startup, V1 baselining, backups, and the `largeTextType` placeholder.

### V2 database extension

| Table / columns | Purpose |
| --- | --- |
| `users.timezone`, `email_verified`, `session_version` | Local dates, verification, old-session rejection after reset |
| `account_tokens` | Hashed, purpose-bound, expiring single-use links |
| `mentor_messages`, `mentor_usage` | Local conversation and unique user/UTC-day quota |
| `quiz_sessions`, `assessment_attempts` | Private question snapshots and one attempt per session |
| `planner_goals`, `planner_tasks` | Weekly targets and tasks with optional skill/roadmap links |
| `progress_events` | Readiness/activity snapshots with historical career IDs |
| `saved_recommendations` | Unique user/slug bookmarks and durable project tombstones |
| `resume_profiles` | One editable JSON resume per user |

All V2 user IDs have foreign keys. Extras records cascade on user deletion; historical career/project/roadmap references intentionally have no foreign key. Large JSON uses CLOB on H2 and LONGTEXT on MySQL through Flyway substitution. Fresh databases run V1/V2; compatible populated V1 schemas receive baseline 1 then V2, retaining existing records and assigning the three user defaults. See [database setup](../database/README.md) and backend-owned [learning](v2-learning-contract.md)/[extras](v2-extras-contract.md) contracts.

## 10. Skill gap algorithm

Let `R` be the distinct required skill IDs of the selected career and `S` the student's recorded skill IDs.

```text
completed = R intersect S
missing   = R minus S
readiness = 0 if R is empty
            otherwise round(100 * size(completed) / size(R), 1 decimal)
```

For a Java career requiring eight skills, a student who records Java, HTML, and CSS has three matched skills and five missing skills: `3 / 8 * 100 = 37.5%`. Proficiency level does not weight this calculation. The algorithm follows configured requirements and has no machine learning model or external AI call.

This is a worked example, not the initial demo state. The supplied demo student additionally has Git and MySQL, giving five matched skills, three missing skills (JavaScript, Spring Boot, REST API), and **62.5%** readiness on a fresh seeded database.

The implementation uses a set for student skill lookup and traverses the required skills. The comparison costs approximately O(S + R), excluding database retrieval. Roadmap synchronization includes additional matching against existing steps; no unmeasured scalability claim is made.

## 11. Roadmap synchronization

Missing career skills create `Learn <skill>` steps in required-skill order. Existing steps are retained, obsolete requirement steps are removed, and recorded skills mark related steps completed. A portfolio capstone is added without a linked skill. Completing a skill step creates a missing user-skill record at Beginner level; reopening the step removes that recorded skill. Updating a profile, modifying skills, and requesting the dashboard synchronize the roadmap. A user must select the step's career before changing its status.

## 12. API and security

The [API reference](api-endpoints.md) documents all specified routes, bodies, validation, and errors. The [Postman collection](CareerForge.postman_collection.json) provides executable request examples.

Passwords are stored using BCrypt and omitted from JSON. Login rotates the server session. Cookies are HttpOnly and SameSite Strict, with a 60-minute idle session timeout. Protected services retrieve the current session user; administrator actions check `ADMIN`; student-record mutations check ownership. A request filter rejects cross-site mutations and sets response security headers. V2 resets increment a private session version to reject old sessions. Recovery and verification store token hashes, purpose, expiry, and used state; raw links are available only through mail/private local files. Generic request responses limit account discovery. The mentor uses server-side Java HttpClient calls to the OpenAI Responses API with store:false; local history still persists. These controls are implementation descriptions, not the result of a penetration test. See [configuration/security](security-configuration.md).

Validation checks required fields, lengths, valid skill/status values, existing IDs, and absolute HTTP(S) resource/project URLs. Duplicate records and missing/forbidden records produce explicit errors. Demonstration accounts are public sample credentials and must not be reused for a real deployment.

## 13. Testing methodology and results

Use [verification](verification.md), the API reference, and Postman to exercise both original and V2 workflows. Record expected/actual behavior, date, profile, and evidence. A written checklist or unexecuted request is not a passed test.

| Evidence | V2 status |
| --- | --- |
| H2 migration execution | Executed on 2026-10-06 with H2 2.3.232 and Flyway 11.7.2 in isolated in-memory databases |
| Fresh H2 Flyway path | V1 and V2 applied; second migrate applied none; validate succeeded |
| Populated V1 H2 upgrade | Baseline 1 then V2; preexisting user ID/name/password hash retained with required defaults; second migrate applied none |
| H2 migration checksum | V2 checksum 965470279 recorded by Flyway; source frozen after this check |
| Live H2 upgrade / Hibernate validation / startup | Passed: original V1 data retained, baseline 1/V2, restart validates migrations and mappings; app on port 8090 |
| MySQL/MariaDB V2 migration and upgrade | Passed on MySQL profile with MariaDB 10.4.32; V1 data retained and Hibernate validation succeeds |
| Backend compilation / integration / runtime API | Maven package passed; 41 Java tests; 17 original + 141 V2 API checks on each database profile |
| Browser / V2 screenshots / Postman execution | 185 V2 and 31 original Chromium checks pass; desktop/390px/320px captures included. Postman not separately executed |
| Real OpenAI response / actual SMTP delivery | Not run without credentials; local HTTP provider fixture and private file-mail tests passed |
| Load / performance / security audit | Not measured |

The 41-test Java run includes all 21 original regression cases, V2 separate-request integration cases, provider/recovery tests, and two rendered PDF layout tests. Both live profiles were checked after packaging. Oracle MySQL was not installed; MariaDB evidence is explicitly labeled. These checks do not establish performance, penetration resistance, or provider delivery without credentials.

## 14. Screenshots for final submission

The following V2 screenshots were captured from the running application on 2026-10-06:

| Figure | Caption / artifact |
| --- | --- |
| 1 | [Student dashboard, desktop](screenshots/v2-dashboard-1440.png): career goal, readiness, and learning/project overview |
| 2 | [Student dashboard, mobile](screenshots/v2-dashboard-390.png): responsive student overview |
| 3 | [Administrator view, desktop](screenshots/admin-desktop.png): administrator workspace |
| 4 | [Assessments](screenshots/v2-assessments-1440.png): curated quizzes and result history |
| 5 | [Study planner](screenshots/v2-planner-390.png): mobile weekly planning |
| 6 | [Resume builder](screenshots/v2-resume-1440.png): editable resume and preview |
| 7 | [Career comparison](screenshots/v2-compare-1440.png): requirements, coverage, and estimates |

See [the screenshot index](screenshots/README.md) for all 15 workspace views at desktop and two mobile widths. Screenshots contain demonstration data, not credentials or raw email tokens.

## 15. Limitations and future work

Readiness depends on self-reported skills and curated career requirements. Resource links are maintained records rather than verified course completion. The API uses local server sessions; recovery and verification require configured SMTP or private file-mail mode. Catalog maintenance and readiness remain deterministic. Quiz scores indicate answers to a curated bank, not certified competence. Mentor output depends on provider availability and must be reviewed; unavailable services remain explicit.

Future work includes independently validated assessments, richer requirement levels, measured learning-time estimates, expanded recommendation catalogs, broader PDF script support, resume parsing, and deployment/security evaluation. Bundled PDF fonts support covered glyphs, with fallback for unsupported scripts; full complex-script shaping is not claimed. These extensions are proposals, not verified capabilities.

## 16. Conclusion

CareerForge organizes student learning around a career goal and makes skill coverage visible through a reproducible comparison. Its main educational contribution is the integration of a relational database, authenticated REST workflows, roadmap synchronization, and portfolio records in a Java application. The final submission should support these descriptions with recorded checks and actual screenshots.

## 17. References

Technology documentation for further study (not claims of an executed verification):

- Java: <https://dev.java/learn/>
- Spring Boot: <https://docs.spring.io/spring-boot/>
- Spring Data JPA: <https://docs.spring.io/spring-data/jpa/reference/>
- Maven: <https://maven.apache.org/guides/>
- H2: <https://h2database.com/html/main.html>
- MySQL: <https://dev.mysql.com/doc/>
- Postman: <https://learning.postman.com/>
- OpenAI Responses API: <https://developers.openai.com/api/reference/python/resources/responses/methods/create>
- Flyway: <https://documentation.red-gate.com/flyway>
- PDFBox: <https://pdfbox.apache.org/>

## Appendix: Viva prompts

| Question | Answer |
| --- | --- |
| Why H2 and MySQL? | H2 reduces demo setup; MySQL demonstrates an external relational database with the same JPA model. |
| Why association tables? | Students and careers each relate to multiple shared skills; joins preserve integrity and avoid repeated skill text. |
| Why hash passwords? | Authentication can compare a password against its BCrypt hash without storing the plaintext password. |
| Is the score AI-generated? | No. It is the percentage of configured required skills present in the user's records. |
| Why sessions? | They provide a straightforward browser login flow using a server-side identity and cookie. |
| Does completing a roadmap step affect skills? | Yes for skill-linked steps; a capstone has no skill association. |
| Why services and repositories? | Services express business rules; repositories isolate persistence access. |
| Can administrators delete any career? | The API rejects deletion while students are following it. |
