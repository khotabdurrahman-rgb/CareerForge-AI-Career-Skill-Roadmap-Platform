# CareerForge frontend

Open the running Spring Boot application at http://localhost:8090/ (or its configured port). This frontend uses the same origin for every API request and cookie sessions. No build step, CDN, external font, or separate frontend server is required.

## Files

- `index.html`: application entry point, accessibility landmarks, notification region, and native dialog.
- `css/app.css`: responsive white workspace, charcoal sidebar, emerald controls, and blue accents.
- `js/app.js`: session API client, hash router, rendering, validation, filtering, dialogs, and student/admin actions.
- `assets/brand.png`: local bitmap brand asset, rasterized from the retained `brand.svg` source.
- `vendor/`: local Bootstrap 5.3.3 CSS, Bootstrap Icons 1.11.3 CSS, and icon fonts. Upstream licenses are retained in vendor source headers; see also `vendor/LICENSES.txt`.

## Screens and workflows

Hash routes: `#login`, `#register`, `#dashboard`, `#skills`, `#careers`, `#gap`, `#roadmap`, `#projects`, `#resources`, `#profile`, `#admin`.

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

Browser checks against the live backend cover all student screens, registration, skills add/edit/remove, roadmap updates, projects create/edit/delete, profile saving, logout, admin career/resource create/edit/delete, and escaped HTML strings. Desktop and mobile checks at 320px, 390px, 768px, and 1440px cover layout, navigation, dialogs, errors, and session expiry. Mobile navigation is hidden and inert when closed; notifications are capped at two on mobile and three on desktop. Demo data is read live; metrics are never hardcoded. QA screenshots are excluded from the public static bundle.
