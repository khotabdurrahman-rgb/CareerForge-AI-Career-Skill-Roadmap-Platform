# Setup in 20 steps

Commands below are for Windows PowerShell. Start in the CareerForge workspace. The default H2 demo needs no MySQL server.

1. **Locate the workspace.** Open the directory containing `backend`, `docs`, `database`, and `scripts`. Paths with spaces must be quoted when passed as command arguments.
2. **Install JDK 17.** A Java runtime alone is insufficient for Maven compilation. This project targets Java 17.
3. **Select the JDK.** Set `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'`, adjusting the path to your installation.
4. **Update the current shell.** Run `$env:Path = "$env:JAVA_HOME\bin;$env:Path"`. This affects only this PowerShell session.
5. **Confirm Java.** Run `java -version` and `javac -version`; both should report 17. The launcher also checks the Java version.
6. **Locate Maven.** Use an installed `mvn.cmd` on `PATH`, or unpack Maven into `.tools/apache-maven-<version>`. The launcher searches both locations.
7. **Check the backend.** Confirm `backend/pom.xml` exists. There is no extra `careerforge-api` directory in the current layout.
8. **Choose an editor.** Open the workspace in IntelliJ IDEA or VS Code with Java support. Let it import the Maven project.
9. **Choose database mode.** Use H2 for the first launch. Choose MySQL only when demonstrating an external database.
10. **Understand persistence.** H2 is file-backed, so saved data survives restart. The configured `./data/careerforge` path resolves to `backend/data/careerforge` when using the launcher.
11. **Prepare optional MySQL.** For MySQL, create/import the database using `database/schema.sql` as described in [database setup](../database/README.md). Skip this step for H2.
12. **Load optional catalog data.** On a fresh MySQL schema, optionally import `database/sample-data.sql` before first application startup. The backend initializes demonstration accounts; do not duplicate an already seeded database.
13. **Set MySQL variables when needed.** Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in this shell. Example URL: `jdbc:mysql://localhost:3306/careerforge`.
14. **Launch the application.** Run `.\scripts\start.ps1` for H2 or `.\scripts\start.ps1 -Profile mysql` for MySQL. The script invokes Maven against `backend/pom.xml`.
15. **Read startup output.** Wait for Spring Boot to finish startup on port 8090. Resolve errors before attempting login; the URL alone is not evidence of a running server.
16. **Open the demo.** Visit `http://localhost:8090` in a browser and sign in with `student@careerforge.dev` / `Career123!`.
17. **Exercise the student workflow.** Update the profile, select a career, add a skill, inspect the dashboard/roadmap, update a roadmap step, and add a project.
18. **Exercise administration.** Log out, sign in with `admin@careerforge.dev` / `Career123!`, view users, and manage a temporary career or resource.
19. **Try the API.** Import `docs/CareerForge.postman_collection.json` into Postman. Keep its cookie jar enabled, run login before protected requests, and replace example IDs with actual returned IDs.
20. **Record and stop.** Follow [verification](verification.md), record actual observations for the college report, and stop the server with `Ctrl+C`. Restart to check persistence. Never describe an unexecuted check as passed.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Java 8 selected | Set `JAVA_HOME` to JDK 17; the launcher can discover `C:\Program Files\Java\jdk-17` and JDK 17 directories in `.tools`. |
| Maven not found | Install Maven or unpack it under `.tools/apache-maven-*/bin/mvn.cmd`. |
| Port 8090 occupied | Identify the process with `Get-NetTCPConnection -LocalPort 8090`; stop your earlier demo instance or intentionally configure another port and update client URLs. |
| MySQL connection rejected | Check server availability, URL, database existence, user grants, and the three environment variables. |
| H2 database locked | Stop the other application process using the same database. Avoid deleting a database to resolve a lock. |
| Protected API rejected | Log in again and retain cookies. Check the account role; sessions expire after 60 minutes of inactivity. |
| Browser cookie missing | Use the same-origin app. Mutating cross-origin requests are rejected; Postman should omit an unrelated `Origin` header. |
| Catalog IDs differ | Fetch actual IDs from the API. SQL and Postman example IDs describe a fresh illustrative dataset. |
