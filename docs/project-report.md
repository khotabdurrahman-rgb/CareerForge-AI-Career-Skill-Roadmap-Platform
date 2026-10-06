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

CareerForge is a Java web application that helps students connect their current skills with a chosen career path. Students maintain profiles, record proficiency levels, identify missing skills, track an ordered learning roadmap, and collect portfolio projects. Administrators maintain career requirements and learning resources. The system uses Spring Boot, Maven, Java 17, Spring Data JPA, session authentication, and BCrypt password hashing. A persistent H2 database enables a local demonstration without installing a database server; a MySQL profile supports an external relational database. Skill recommendations are deterministic set comparisons rather than AI predictions. The project demonstrates authentication, relational modelling, REST APIs, business logic, and a complete student workflow.

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

Job placement, verified proficiency assessment, real-time job feeds, resume parsing, and external AI integration are outside the current scope. A readiness score indicates coverage of configured requirements, not a probability of employment.

## 5. Existing and proposed system

| Dimension | Manual/disconnected approach | CareerForge approach |
| --- | --- | --- |
| Student records | Notes and separate documents | Profile and related database records |
| Career requirements | Repeated manual research | Administrator-maintained requirements |
| Skill gaps | Manually compare lists | Compare distinct skill identifiers |
| Progress | Separate checklists | Roadmap linked to recorded skills |
| Portfolio | Scattered links | Projects associated with a student |
| Resources | General bookmarks | Resources associated with skills |

The proposed approach is feasible for a mini-project because it requires no paid external service. Java and Maven support reproducible builds, while H2 reduces demonstration setup. MySQL allows the same relational design to be explained and evaluated independently.

## 6. Requirements

| Category | Requirement |
| --- | --- |
| Development runtime | JDK 17 and Maven; workspace Maven fallback supported |
| Application | Spring Boot backend rooted at `backend/pom.xml`, default port 8090 |
| Data | Default H2 file database; optional MySQL profile |
| Client | Modern browser; Postman for manual API checks |
| Functional | Authentication, profile, career selection, skills, gap analysis, roadmap, projects, administration |
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

The model avoids comma-separated skill identifiers by using association tables. Unique emails, unique skill names, unique user-skill pairs, and unique roadmap titles per user/career prevent duplicate records. Foreign keys enforce reference integrity. Profile year is text, such as `Third Year`. Readiness is calculated instead of stored, reducing stale derived values. The executable design is supplied in [schema.sql](../database/schema.sql).

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

Passwords are stored using BCrypt and omitted from JSON. Login rotates the server session. Cookies are HttpOnly and SameSite Strict, with a 60-minute idle session timeout. Protected services retrieve the current session user; administrator actions check `ADMIN`; student-record mutations check ownership. A request filter rejects cross-site mutations and sets response security headers. These controls are implementation descriptions, not the result of a penetration test.

Validation checks required fields, lengths, valid skill/status values, existing IDs, and absolute HTTP(S) resource/project URLs. Duplicate records and missing/forbidden records produce explicit errors. Demonstration accounts are public sample credentials and must not be reused for a real deployment.

## 13. Testing methodology and results

Use [the verification checklist](verification.md) and Postman collection to exercise authentication, student workflows, administrator workflows, invalid input, ownership, and persistence. Perform H2 and MySQL checks separately; MySQL startup should validate the imported schema. Record expected behavior, actual behavior, evidence, and date for each executed case.

| Evidence | Current report status |
| --- | --- |
| Backend compilation | Successful on 2026-10-06 using JDK 17 and Maven 3.9.9 |
| Spring integration tests | 21 passed on 2026-10-06; zero failures, errors, or skips; in-memory H2 |
| Runtime API verification | `qa/check-api.cjs` PASS on 2026-10-06: 17 assertions each on H2 port 8090 and MySQL-profile/MariaDB port 8091 |
| Database schema import | Successful on XAMPP MariaDB 10.4, isolated data directory, port 3307 |
| MySQL-profile startup / schema compatibility | Live API checks passed using MariaDB 10.4 on app port 8091 |
| Oracle MySQL execution | Not tested; Oracle MySQL was not installed |
| Browser checks | `qa/check-browser.cjs` PASS on 2026-10-06: 31 assertions across desktop/mobile workflows; no captured page errors or HTTP 5xx responses |
| UI screenshots | Desktop dashboard, mobile dashboard, and desktop administration captured in `docs/screenshots` |
| Load/performance/security testing | Not measured |

Integration evidence is recorded in `backend/target/surefire-reports/com.careerforge.qa.WorkspaceApiIntegrationTest.txt`. A written checklist or an unexecuted Postman assertion is not a passed test result.

Live coverage includes unauthorized and wrong-credential responses, password omission, initial 62.5% readiness, completion to 75% and reversion to 62.5%, project save/read/delete, rejection of JavaScript URLs and cross-origin mutations, administrator listing, ownership isolation, and logout. The integration suite additionally exercises registration validation, session rotation, administrator CRUD, skill upsert, career switching, and related error cases. Browser checks cover routes, skill/project mutations, roadmap completion/reversion, career switch/reversion, mobile navigation, and administrator listing/career/resource mutations. These results cover the executed checks; the separate Postman collection, load testing, and Oracle MySQL execution have not been verified. The packaged application was restarted successfully; the demo's five skills, three projects, and 62.5% readiness persisted.

## 14. Screenshots for final submission

The following actual screenshots were captured on 2026-10-06:

| Figure | Caption / artifact |
| --- | --- |
| 1 | [Student dashboard, desktop](screenshots/dashboard-desktop.png): career goal, readiness, and learning/project overview |
| 2 | [Student dashboard, mobile](screenshots/dashboard-mobile.png): responsive student overview |
| 3 | [Administrator view, desktop](screenshots/admin-desktop.png): administrator workspace |

See [the screenshot index](screenshots/README.md). Additional login, skills/gap, roadmap, profile, portfolio, and Postman screenshots may be captured for an expanded submission; they are not included as existing evidence. Remove session cookies and unnecessary personal data from any additional captures.

## 15. Limitations and future work

Readiness depends on self-reported skills and curated career requirements. Resource links are maintained records rather than verified course completion. The current API uses local server sessions and does not include account recovery or email verification. Catalog maintenance and scoring remain deterministic.

Possible extensions include proficiency assessment, richer requirement levels, user-managed career comparisons, course progress, email verification, resume analysis, and reviewed AI suggestions. These are proposals, not current capabilities.

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
