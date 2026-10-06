# Manual verification checklist

## Public-site hardening - 2026-10-07

The full Maven test run passes **48 tests** with zero failures/errors. Seven new checks cover per-IP operation isolation, validation-before-throttle ordering, retry headers, concurrent admission, bounded bucket cleanup, minimal health exposure, and database DOWN/readiness versus liveness behavior. The existing 41 tests remain passing.

Live API suite: **142 PASS** against the public Render URL and **142 PASS** against the updated local H2 app, with zero paid AI requests. The public run creates two synthetic QA accounts and only mutates owned QA records. The original local browser suite also passes **31 checks** after the changes.

Public-mode Chromium browser suite: **186 PASS**, covering 15 workspace routes and four recovery routes at 1440, 390, and 320 pixels plus quiz, planner, resume/PDF, comparison, recommendations, and progress workflows. No captured browser exceptions or HTTP 5xx errors. It creates two additional synthetic QA accounts and stores screenshots under `docs/screenshots/public`; no email tokens or provider credentials are captured.

SMTP delivery, a real OpenAI call, external database durability, uptime-alert delivery, and persistence after a hosted restart remain unverified without owner configuration. No production restart or storage migration was performed during the initial live checks. The user subsequently authorized publishing with possible temporary-data resets; the new health endpoints and auth limits require deployment of this update.

## V2 executed evidence - 2026-10-06

- Maven `package` succeeds: **41 tests, zero failures/errors** across seven suites, including the 21 original regression tests and 20 V2/PDF tests. Reports: `backend/target/surefire-reports`.
- Persistent H2: original populated V1 database backed up privately, baselined at 1, migrated to V2, and restarted with Flyway checksum and Hibernate schema validation. Existing demo records/readiness remain intact.
- MySQL profile against isolated MariaDB 10.4.32 on port 3307: existing V1 schema upgraded to V2; subsequent startup validates both migration checksums and Hibernate mappings. Oracle MySQL was not installed or separately tested.
- `qa/check-api.cjs`: **17 PASS** on each profile. `qa/check-v2-api.cjs`: **141 PASS** on each profile. Covers server-only quiz scoring, ownership, separate-request persistence, planner/roadmap links, PDF bytes, comparison, recommendation idempotency, progress reads, unavailable AI/email, and authentication.
- Local HTTP mentor fixture tests exercise actual Responses request serialization, context, response parsing, provider errors, and timeout. No paid API requests were made. Recovery tests cover hashed expiring/single-use tokens, private file delivery, neutral responses, rate limits, and invalidation of previous sessions.
- Two PDF layout tests verify selectable Unicode text, visibility controls, long-word wrapping, multi-page content retention, and glyph bounds. Rendered first/last pages in `backend/target/pdf-qa` were visually inspected.
- Chromium browser: **185 V2 checks PASS**, plus **31 original regression checks PASS**. All 15 workspace routes and four recovery routes were checked at 1440, 390, and 320 pixels. Workflows include quiz feedback, planner goal/task mutations, resume persistence/toggles/download, comparison, saved projects, history, and unavailable mentor. No browser exceptions or captured HTTP 5xx responses. Scrollable tables stay within the page; their deliberately scrollable contents are not mistaken for page overflow.

Real OpenAI and external SMTP delivery remain untested without credentials. File delivery is not SMTP evidence. Postman collection execution, Oracle MySQL, load testing, and penetration testing were not performed.

Additional isolated H2 Flyway probes covered fresh V1/V2 creation, populated V1 baseline/upgrade, default values, and no-op second migration. The private V1 backup is not a public report attachment. Applied SQL must not be edited; schema changes need a new migration version.

## Historical V1 evidence

The earlier results dated 2026-10-06 recorded **21 Spring integration tests**, **17 live API assertions on each profile**, and **31 browser assertions**. They predate V2 and must not be presented as V2 results. Record profile, date, expected/actual behavior, and evidence for all new checks. Keep raw email tokens and session cookies out of screenshots.

Backend compilation succeeded using `.tools/apache-maven-3.9.9` and `C:\Program Files\Java\jdk-17`. `WorkspaceApiIntegrationTest` uses an in-memory H2 database; its Maven Surefire report records **Tests run: 21, Failures: 0, Errors: 0, Skipped: 0**. Evidence: `backend/target/surefire-reports/com.careerforge.qa.WorkspaceApiIntegrationTest.txt`. The suite covers authentication/session rotation, password omission, protected routes, ownership, project CRUD, admin CRUD/authorization, skill upsert/gap calculations, roadmap completion/reversion, career switching, validation, and origin/fetch-metadata checks.

The MySQL schema imported successfully into isolated XAMPP MariaDB 10.4 on database port 3307. `qa/check-api.cjs` returned **PASS, 17 assertions** against **H2 / http://localhost:8090** and separately **MySQL profile + MariaDB 10.4 / http://localhost:8091**.

Live coverage: unauthenticated/wrong-credential 401 responses, password omission, fresh-demo 62.5% skill gap, roadmap completion increasing readiness to 75% and reversion to 62.5%, project save/read/delete, rejection of a JavaScript URL, cross-origin mutation rejection, administrator listing, record ownership isolation, and logout. The packaged app was restarted and rechecked: five demo skills, three projects, 62.5% readiness, and sequential roadmap positions persisted. A separate-transaction regression test covers skill removal and career switching.

`qa/check-browser.cjs` returned **PASS, 31 assertions** covering desktop/mobile routes, skill add/remove, roadmap completion/reversion, project add/delete, career switch/reversion, mobile navigation, administrator listing, and career/resource add/delete. No page errors or HTTP 5xx responses were captured by its error checks. Screenshots: [desktop dashboard](screenshots/dashboard-desktop.png), [mobile dashboard](screenshots/dashboard-mobile.png), and [desktop administration](screenshots/admin-desktop.png).

These results do not imply every planned case below was executed. The Postman collection has not been executed as evidence. Oracle MySQL was not installed or tested. Restart persistence, load measurement, and penetration testing remain unverified.

| ID | Action | Expected behavior |
| --- | --- | --- |
| SET-01 | Launch with Java 17 and installed/fallback Maven | Startup completes on port 8090 |
| SET-02 | Launch with no compatible JDK or Maven | Launcher explains the missing prerequisite |
| DB-01 | Start default profile without MySQL | H2 file-backed demo starts |
| DB-02 | Create empty MySQL database only; launch mysql profile | Flyway runs V1/V2, then Hibernate validates |
| DB-03 | Optional sample seed on empty migrated catalog | No explicit-ID collisions; catalog references valid |
| AUTH-01 | Login with student demo credentials | Session established; `/api/me` returns student without password hash |
| AUTH-02 | Use incorrect password | 401 with message |
| AUTH-03 | Register unique email and valid password | Student created and signed in |
| AUTH-04 | Register duplicate email | 409; no duplicate user |
| AUTH-05 | Logout, then request `/api/me` | Protected request returns 401 |
| SEC-01 | Student requests admin users | 403 |
| SEC-02 | Student mutates another student's skill/project/step | 403; target record unchanged |
| SEC-03 | Send mutation with unrelated Origin | 403 |
| PROFILE-01 | Save profile and valid career ID | Profile persists; selected-career roadmap refreshed |
| PROFILE-02 | Use nonexistent career ID | 404 |
| SKILL-01 | Add catalog skill at Beginner | One user-skill record; readiness reflects matched requirements |
| SKILL-02 | Add same skill at Intermediate | Existing record updated; no duplicate |
| SKILL-03 | Send unsupported level | 400 |
| SKILL-04 | Delete own user-skill ID | Record removed; related roadmap recalculated |
| GAP-01 | Record three of eight required skills | Readiness 37.5; remaining five appear missing |
| GAP-02 | Read untouched fresh demo student's dashboard | Java/HTML/CSS/Git/MySQL match 5/8; readiness 62.5; three skills missing |
| ROAD-01 | Complete skill-linked step | Status completed; related user skill added if absent |
| ROAD-02 | Reopen completed skill-linked step | Related user skill removed; readiness decreases if required |
| ROAD-03 | Complete capstone | No skill created; readiness unchanged |
| ROAD-04 | Update step from unselected career | 400 |
| PROJECT-01 | Create project then delete its ID | Project appears in dashboard then disappears |
| PROJECT-02 | Use invalid URL/status | 400 |
| ADMIN-01 | Login as admin; list users | User list without password hashes |
| ADMIN-02 | Create/update/delete temporary unused career | Requirements persist; deletion succeeds |
| ADMIN-03 | Delete a career followed by a student | 400; career retained |
| ADMIN-04 | Create/delete temporary resource | Resource appears then disappears from dashboard |
| DATA-01 | Restart after changes | Saved profile/skills/projects persist |
| UI-01 | Check desktop and narrow browser viewport | Navigation and form text remain usable without overlap |

## V2 checks - completion tracked separately

| ID | Action | Expected behavior |
| --- | --- | --- |
| V2-DB-01 | Fresh H2 and fresh MySQL startup | Fresh H2 migration probe passed; fresh MySQL startup not separately run |
| V2-DB-02 | Upgrade a backed-up populated V1 database | Live H2 and MySQL-profile/MariaDB upgrade and validation passed |
| V2-DB-03 | Restart migrated database | Both live profiles validate previously applied migrations and start successfully |
| V2-DB-04 | Exercise unique pairs and foreign keys | Duplicate tokens/usage/attempts/bookmarks/resumes rejected |
| V2-CONFIG | Start with AI/SMTP variables absent | Core workflows usable; service status unavailable |
| V2-MAIL | Use smtp-file and request reset/verification | Private mail files contain links; responses remain generic and token-free |
| V2-TOKEN | Expired/wrong-purpose/reused token | Rejected without changing account |
| V2-RESET | Reset password while signed in on another session | New password works; all prior sessions rejected |
| V2-VERIFY | Consume valid verification token | emailVerified persists; repeat rejected |
| V2-MENTOR | Isolated local Responses HTTP test via URL property override | Actual REST body/auth parsing, store:false, context, history, errors, and timeout verified without a real key |
| V2-QUOTA | Exhaust daily allowance; clear history | 429 at limit; history clear does not restore quota |
| V2-AI | Explicitly configured real OpenAI call | Provider result recorded separately; pending without credentials |
| V2-SMTP | Real SMTP delivery | Actual mailbox arrival recorded separately from file mode |
| V2-QUIZ | Start/submit/expire/repeat and cross-user session | Safe start DTO, server score, 410/409/404; self-report unchanged |
| V2-PLAN | Goals/tasks, timezone boundaries, overdue and linked completion | Correct local week/totals, ownership, and roadmap side effects |
| V2-RESUME | Save fields and download Unicode/multipage PDF | Persisted fields; readable valid PDF; owner isolation |
| V2-COMPARE | Compare two careers | Coverage/estimates accurate; active career unchanged |
| V2-RECOMMEND | Save/unsave and add portfolio twice | Owned state and idempotent project behavior |
| V2-PROGRESS | Record activity then GET repeatedly | Readiness snapshots/streak correct; reads create no events |
| V2-UI | Desktop/mobile V2 views and service-unavailable states | No overlap; controls and validation usable |

## Evidence log

| Check ID | Date / profile | Actual behavior | Evidence location | Result |
| --- | --- | --- | --- | --- |
| [ID] | [Date; H2/MySQL] | [Observed output] | [Log or screenshot path] | [Pass/Fail/Not run] |

## Postman sequencing

Login as student -> read catalog/dashboard -> inspect captured IDs -> perform chosen student mutations. Logout before the unauthenticated example. Login as student before the forbidden-admin example. Login as administrator before temporary career/resource operations. The collection captures created IDs so its delete requests target those records. Running registration creates an additional student; no account-deletion endpoint is provided.

## Build verification

To reproduce the integration run, select Java 17 and run installed Maven `mvn -f backend/pom.xml test`, or use the workspace fallback:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& '.\.tools\apache-maven-3.9.9\bin\mvn.cmd' -f backend/pom.xml test
```

For live API checks against an already running server:

```powershell
$env:CAREERFORGE_URL = 'http://localhost:8090'
node qa/check-api.cjs
$env:CAREERFORGE_URL = 'http://localhost:8091'
node qa/check-api.cjs
$env:CAREERFORGE_URL = 'http://localhost:8090'
node qa/check-v2-api.cjs
node qa/check-v2-browser.cjs
```

The live script expects the demo student's 62.5% baseline and makes temporary mutations before restoring readiness/deleting its project. Use an unchanged demo dataset. These commands describe reproduction; the dated results above record the completed runs.

## Optional browser QA tools

Node.js/npm and Playwright are **development QA tools**, not requirements for normal Java/Maven application startup. From the workspace root, install the tools locally and run against an already running demo:

```powershell
npm install --prefix .tools playwright
& '.\.tools\node_modules\.bin\playwright.cmd' install chromium
$env:CAREERFORGE_URL = 'http://localhost:8090'
node qa/check-browser.cjs
```

The browser script uses the demo accounts, makes temporary changes, and writes PNGs to `docs/screenshots`. Review the current dataset before rerunning. Its executed results cover its configured Chromium desktop/mobile viewports, not all browsers or devices.
