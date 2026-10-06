# Render launch checklist

Live site: https://careerforge-ai-career-skill-roadmap.onrender.com

## Set privately in Render

Open your service's Environment settings. Do not put secrets into `.env.example`, GitHub, screenshots, or chat.

```text
APP_BASE_URL=https://careerforge-ai-career-skill-roadmap.onrender.com
SMTP_HOST=<your-provider-host>
SMTP_PORT=587
SMTP_USERNAME=<your-mail-account>
SMTP_PASSWORD=<provider-app-password-or-SMTP-secret>
SMTP_AUTH=true
SMTP_STARTTLS=true
MAIL_FROM=<verified-sender-address>
OPENAI_API_KEY=<new-OpenAI-key>
SPRING_PROFILES_ACTIVE=public,mysql
DB_URL=<provider-JDBC-URL-with-required-TLS>
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
```

These are placeholders, not valid credentials. Confirm your provider's port/TLS/sender requirements before using them. Replace any API key previously shared in chat. CareerForge's current mentor uses OpenAI; another provider's key will not work without an adapter.

Without an explicit `APP_BASE_URL`, the app uses Render's automatically provided `RENDER_EXTERNAL_URL`, then localhost outside Render. If you already set `APP_BASE_URL` to localhost, change or remove that override. [Render environment variables](https://render.com/docs/environment-variables).

## Preserve data

Changing from H2 to MySQL does not transfer existing accounts automatically. Back up/export the current database before changing profiles; stop writes while migrating. The additive Flyway migrations preserve compatible data within the same database, not across different databases. MySQL profile creates a schema in an already-created database; public-profile catalog seeding does not create known demo accounts.

With paid persistent H2 storage instead, retain `SPRING_PROFILES_ACTIVE=public` and mount the approved disk at `/app/data`. Do not use local H2 on Render's ephemeral filesystem for lasting student records. No database/disk was purchased or switched by this update.

## Health and alerts

After deploying this update, set Render's Health Check Path to `/actuator/health/readiness`. It returns 200 with `{"status":"UP"}` when the application and database are healthy. Configure an external uptime alert at the same URL using an account you control; no alert service or billing plan is enabled automatically.

## Acceptance checks

- Register a unique account and receive the verification email; normal responses/UI must not expose raw tokens.
- Follow the email link; email verification must persist and a reused link must fail.
- Request password reset while signed in on another device; reset using email, confirm the new password works and the previous session is rejected.
- Submit/retake a skill assessment and inspect history; declared skill readiness must remain distinct from the score.
- Save a weekly task, complete it, and check linked roadmap changes.
- Save a resume, reload, and download/select PDF text.
- Configure a replacement OpenAI key; submit one mentor question and inspect its answer/history. Paid usage requires the account owner's configuration/approval.
- Restart the service only after verifying storage configuration and backup. Confirm account, tasks, assessments, and resume persist. A restart of ephemeral H2 may erase them; do not perform it as an unannounced production test.

Credential delivery, actual SMTP arrival, real OpenAI calls, provider backup policy, and persistence across a hosted restart remain configuration-dependent; local fixtures alone cannot certify them.
