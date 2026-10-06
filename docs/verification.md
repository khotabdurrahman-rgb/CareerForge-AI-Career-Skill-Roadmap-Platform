# Manual verification checklist

**Results dated 2026-10-06:** **21 Spring integration tests passed**, **17 live API assertions passed on each of two profiles**, and **31 browser assertions passed**. The broader checklist below includes planned checks beyond that evidence. Record date, profile, expected/actual behavior, and evidence for additional checks. Use disposable local data for mutations. Do not expose session cookies in report screenshots.

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
| DB-02 | Import MySQL schema; launch mysql profile | JPA validation accepts tables and application connects |
| DB-03 | Optional sample seed on fresh MySQL | Catalog references are valid; startup creates demo accounts |
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

## Evidence log template

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
