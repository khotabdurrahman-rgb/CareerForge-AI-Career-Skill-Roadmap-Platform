# Collaboration and design review

Review date: **2026-10-06**. CareerForge was developed through delegated work with explicit file ownership and a shared backend contract.

## Actual collaboration

| Contributor | Responsibility |
| --- | --- |
| Parent agent: backend and coordination | Spring Boot/Maven implementation, JPA models, session authentication, password hashing, services, API contract, runtime integration, and coordination |
| Frontend agent | Browser interface and student/administrator workflows, aligned with the REST contract |
| Documentation agent | README, 20-step setup, college report, SQL schema/sample catalog, API reference, Postman collection, launcher, and evidence documentation |
| QA agent | Spring integration tests, live API verification, and browser verification work |

The three delegated agents worked on frontend, documentation, and QA while the parent implemented the backend. Documentation ownership was limited to `docs/**`, `database/**`, `README.md`, `.gitignore`, `LICENSE`, and `scripts/**`. Backend models and request DTOs were read to align documentation and SQL with the actual implementation. Test results and screenshots were incorporated as the checks completed.

The initial [STEP-1-ARCHITECTURE.md](../STEP-1-ARCHITECTURE.md) describes three earlier planning roles: **Backend Architecture Agent**, **UI/UX Design Agent**, and **Judge Agent**. Those are the earlier architecture discussion, not the current implementation team. Its competition framing is a planning narrative; this review does not claim independent competitive trials, numeric scores, or measured rankings.

## Selected design

The selected design combines a layered Java 17 Spring Boot REST application, JPA-backed relational data, server sessions, and a browser dashboard. H2 provides the default persistent demonstration database; a MySQL profile supports an imported external schema. Career recommendations use a transparent skill-set comparison. This preserves the initial layered architecture while simplifying first-run setup.

The winning design was assessed qualitatively against four practical criteria:

| Criterion | Design assessment | Evidence and limits |
| --- | --- | --- |
| Implementability | A single Maven backend, predefined skills, and deterministic rules keep the mini-project scope manageable. | Backend compiled; 21 Spring integration tests passed. AI and external job feeds remain future work. |
| Usability | Profiles, career requirements, gaps, roadmap progress, and projects form one student workflow; administration maintains shared records. | 31 browser assertions passed across desktop/mobile student and administrator workflows. Three screenshots document rendered views. This is workflow evidence, not a user study. |
| Maintainability | Controllers, services, repositories, DTOs, and entity relationships separate request handling, business rules, validation, and persistence. | SQL/API documentation was aligned with JPA models; shared skills use association tables. Continued maintainability depends on updating these contracts with code changes. |
| Demo readiness | H2 avoids an external database requirement; demo accounts, a launcher, a setup guide, and an optional database profile support reproduction. | 17 live assertions passed per profile on H2 and MariaDB 10.4; 31 browser assertions passed. Oracle MySQL was not installed or tested. |

This assessment explains the selection without invented scores. Sessions suit a local browser demonstration; the MySQL profile demonstrates relational portability; the deterministic comparison makes the readiness calculation explainable during a viva. The design does not establish employment prediction accuracy or verified student proficiency.

## Verification record

On 2026-10-06, `WorkspaceApiIntegrationTest` completed **21 tests with zero failures, errors, or skips** using in-memory H2. Maven Surefire evidence is at `backend/target/surefire-reports/com.careerforge.qa.WorkspaceApiIntegrationTest.txt`.

`qa/check-api.cjs` passed **17 assertions** against H2 at `http://localhost:8090` and independently **17 assertions** against the MySQL profile backed by XAMPP MariaDB 10.4 at `http://localhost:8091`. Checks covered authentication, password omission, skill gap/readiness changes, project save/read/delete, unsafe URL rejection, cross-origin rejection, administrator access, ownership isolation, and logout.

`qa/check-browser.cjs` passed **31 assertions** across desktop/mobile routes, skill and project mutations, roadmap completion/reversion, career switch/reversion, mobile navigation, and administrator career/resource workflows. No page errors or HTTP 5xx responses were captured. [Three screenshot artifacts](screenshots/README.md) document the desktop dashboard, mobile dashboard, and administrator view.

These are separate test runs rather than an aggregate score. The MariaDB run is compatibility evidence, not an Oracle MySQL result. See [verification.md](verification.md) for reproducible commands, coverage, and the remaining checklist.
