# CareerForge frontend

Open the running Spring Boot application at http://localhost:8090/ (or its configured port). This frontend uses the same origin for every API request and cookie sessions. No build step, CDN, external font, or separate frontend server is required.

## Files

- `index.html`: application entry point, accessibility landmarks, notification region, and native dialog.
- `css/app.css`: responsive white workspace, charcoal sidebar, emerald controls, and blue accents.
- `js/app.js`: session API client, hash router, rendering, validation, filtering, dialogs, and student/admin actions.
- `js/v2.js`: route-local V2 requests, assessments, planner, resume drafts/PDF, comparison, recommendations, history, mentor, and account recovery. Receives shared helpers from the existing app closure.
- `css/v2.css`: grouped scrolling navigation and responsive V2 tool layouts.
- `assets/brand.png`: local bitmap brand asset, rasterized from the retained `brand.svg` source.
- `vendor/`: local Bootstrap 5.3.3 CSS, Bootstrap Icons 1.11.3 CSS, and icon fonts. Upstream licenses are retained in vendor source headers; see also `vendor/LICENSES.txt`.

## Screens and workflows

Hash routes: `#login`, `#register`, `#dashboard`, `#skills`, `#careers`, `#gap`, `#roadmap`, `#projects`, `#resources`, `#profile`, `#admin`.

V2 workspace routes: `#assessments`, `#planner`, `#resume`, `#compare`, `#recommendations`, `#history`, `#mentor`. Recovery routes: `#forgot-password`, `#verification`, `#reset-password?token=...`, `#verify-email?token=...`. Recovery routes work with or without a signed-in session and incoming links survive the initial session check.

## V2 contracts and ownership

All frontend changes are contained in `backend/src/main/resources/static/**`; backend endpoints, migrations, dependencies, and server configuration remain owned by the parent/backend agents.

- Assessments: `GET /api/assessments`, `POST /api/assessments/{skillId}/start`, and `POST /api/assessments/sessions/{id}/submit` with `{answers:[{questionId,optionIndex}]}`. Submission accepts both flat attempt fields with feedback and the backend's `{attempt,feedback}` envelope. Questions use server option strings; scoring and explanations come from the server. Answers remain in memory across hash navigation; ending an attempt requires confirmation, and reload/close prompts while an attempt is active.
- Planner: `GET /api/planner?week=YYYY-MM-DD`, plus POST/PUT/DELETE goals and tasks at `/api/planner/goals[/id]` and `/api/planner/tasks[/id]`. Forms include week, due date, minutes, status, optional skill and roadmap links. Inline status updates send the full task contract. Completed linked tasks refresh the V1 roadmap. Week selection defaults to Monday in the profile timezone.
- Resume: `GET /api/resume`, `PUT /api/resume` with direct profile fields and boolean section flags, and authenticated `GET /api/resume/pdf`. The editable preview escapes all content. Unsaved drafts survive navigation, trigger a reload warning, and offer save-and-download confirmation before PDF export.
- Comparison: `GET /api/careers/compare?left=...&right=...`; renders both server gaps and hour estimates, shared skills, and the server estimate explanation.
- Recommendations: `GET /api/recommendations`, POST/DELETE `/api/recommendations/{id}/save`, and POST `/api/recommendations/{id}/portfolio`. Displays prerequisites, technologies, milestones, and related careers; portfolio creation refreshes V1 projects.
- History: `GET /api/progress`; renders actual events, readiness trend, streak, timezone, and the server streak definition. No synthetic trend or history is generated.
- Mentor: GET/POST `/api/mentor` and DELETE `/api/mentor/history`. Renders actual message history and server availability/quota. Sending is disabled when unavailable or out of messages; clearing history requires confirmation. No fallback AI replies or client API keys.
- Recovery: `GET /api/auth/config`, POST `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/auth/verification`, and `/api/auth/verify-email`. Email requests show neutral server messages; email availability and local delivery status come from config. Incoming tokens are only read from the link for submission, never printed or stored. Successful consumption removes tokens from the URL. Password reset ends the current session because the backend invalidates it.
- Profile: timezone is included in `PUT /api/profile` and preserved when changing careers; email verification status and recovery links appear in account security.

V2 requests start independently on route entry and refresh on re-entry, with local loading/error/retry states. Dashboard failures do not overwrite an independently loaded V2 screen. Session/logout clears V2 response caches, pending request ownership, quiz answers, and drafts. Generation guards discard results from a previous session. API strings use the existing escaping helpers and static same-origin assets.

Student workflows include registration/login/logout, adding/updating/removing skills, career selection, gap analysis, roadmap status changes, project creation/editing/removal, resource search/filtering, and profile updates. Admin adds/edits/removes career paths and resources and searches user records. Admin navigation and actions require the session user's `ADMIN` (or `ROLE_ADMIN`) role; the API remains responsible for authorization.

Demo buttons submit `student@careerforge.dev` or `admin@careerforge.dev` with `Career123!` to the real login endpoint. They do not fabricate data or bypass authentication. Reloading the page restores the session using `GET /api/me`.

## Contract assumptions

- `/api/me` returns the user directly; a `{user: ...}` envelope is also accepted. Other catalogs return arrays; `/api/dashboard` follows the supplied aggregate shape.
- Skill levels are case-sensitive `Beginner`, `Intermediate`, `Advanced`. Posting an existing catalog skill updates its level.
- `currentYear` is a string (for example `Year 2`); IDs are numeric.
- Project status is `IN_PROGRESS` or `COMPLETED`; roadmap status additionally supports `NOT_STARTED`.
- Profile writes require a valid `careerId`. Selecting a career writes all existing profile fields and the new goal; the backend generates/recalculates the roadmap and gap.
- Dashboard readiness is already a percentage in the range 0-100; it is not converted from a fraction.
- Resource type is a string. Forms offer common types and retain existing catalog types when editing.
- `PUT /api/projects/{id}` and `PUT /api/admin/resources/{id}` are available for edit workflows.
- Session cookies are sent with `credentials: 'same-origin'`. The backend validates same-origin requests and does not require a separate CSRF token.
- Errors may use `{message}`, `{detail}`, or `{errors}`. Authentication expiry returns HTTP 401.

All API and user text is escaped before rendering. External URLs must use HTTP(S) and open with `noopener noreferrer`. Passwords/session tokens are never persisted in browser storage. Requests have a 20-second timeout, forms prevent duplicate saves, delete operations require confirmation, and successful mutations refresh the aggregate workspace.

## Verification

V2 verification used Playwright with API contract fixtures and the actual static files: all seven V2 screens and profile were checked at 320, 390, 768, and 1440 pixels without horizontal page overflow or JavaScript errors. Covered assessment submission and retained answers, planner task create/status/delete, resume retained drafts/save/PDF request, recommendation save/portfolio, mentor enabled/unavailable, history failures/retry without looping, neutral recovery requests, and successful reset token removal. Additional checks passed for nested attempt submissions and independent V2 loading with a failed dashboard. The extended fixture run then timed out because it attempted a V1 profile route while its dashboard was still unavailable; that scenario now offers a workspace retry. Screenshots are written to the OS temp directory, not the public bundle.

The process on port 8090 initially did not expose `/api/auth/config` during fixture verification (404); the parent subsequently started V2 2.0.0 with successful migrations. The parent/browser QA agent owns `qa/check-v2-browser.cjs`, final packaging, and the coordinated live workflow run. The earlier V1 verification below describes the established baseline, not a new V2 live-backend pass.

Final read-only live smoke passed using the current source static assets with the running V2 server: 27 route/viewport checks covering dashboard, every V2 screen, and profile at 320, 390, and 1440 pixels. No visible API errors, horizontal page overflow, or JavaScript errors. Real demo login/session and live API responses were used; screenshots remain in the OS temp directory. No Maven build or server restart was run by the frontend agent. Dashboard now links directly to planner, assessments, and progress history.

Browser checks against the live backend cover all student screens, registration, skills add/edit/remove, roadmap updates, projects create/edit/delete, profile saving, logout, admin career/resource create/edit/delete, and escaped HTML strings. Desktop and mobile checks at 320px, 390px, 768px, and 1440px cover layout, navigation, dialogs, errors, and session expiry. Mobile navigation is hidden and inert when closed; notifications are capped at two on mobile and three on desktop. Demo data is read live; metrics are never hardcoded. QA screenshots are excluded from the public static bundle.
