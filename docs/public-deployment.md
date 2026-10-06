# Public deployment

GitHub hosts the project source, not a running Java server. Deploy the root Dockerfile to a Docker-capable host such as Render or Railway. This repository does not contain hosting credentials or a provisioned public service.

## Render setup

1. Sign in at https://dashboard.render.com and create a Web Service.
2. Connect this GitHub repository and select branch `main`.
3. Select Docker runtime, repository-root context, and `./Dockerfile`.
4. Set `SPRING_PROFILES_ACTIVE=public,mysql`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for an external MySQL database. Require database TLS according to your provider; do not disable certificate verification.
5. Set `APP_BASE_URL` to the HTTPS URL Render assigns, and health-check path `/api/auth/config`.
6. Choose and confirm your hosting plan yourself. No paid resources have been created by this project setup.
7. Deploy, create your own student account, and verify login, assessment submission, planner saves, and PDF download. Restart the service and confirm saved data survives before inviting students.
8. Optional: set AI/SMTP secrets only in the host's environment settings. See [configuration](security-configuration.md).

The public profile enables secure cookies and trusted-proxy forwarding and disables the publicly known student/admin demo accounts. It seeds career/skill/resource catalogs but not accounts. Demo sign-in controls are hidden when seeding is disabled. This does not remove demo accounts from an existing database: never use the local demonstration database for a public service. Use a new, private database. Do not create a public admin account with the documented demo password.

## Storage and cost

Render's [free services](https://render.com/docs/free) have ephemeral filesystems and may sleep. The default H2 file database loses changes when that environment restarts or redeploys. A free H2 deployment is only disposable testing, not durable student storage. Use external MySQL or a paid persistent disk; persistent disks and paid services require explicit budget approval. With a disk, use the public profile, mount at `/app/data`, and keep the H2 URL unchanged.

The Docker image contains code, bundled fonts, migrations, and test sources during its build stage. It does not copy `.env`, local database files, private mail, or workspace tools. Java runs as a non-root user. No Docker engine was available in the development environment, so the container build must be verified by the host; Maven and the Java application are tested separately.

After a successful deploy, place the actual HTTPS URL in GitHub's About/Website field and README. Do not advertise a guessed `onrender.com` address as live.
