# CareerForge V2 Learning Contract

Owned files: `backend/src/main/java/com/careerforge/v2/learning/**` and
`backend/src/main/resources/assessments.json`. No global configuration changes.
Requires the parent's `User.timezone` field (default `Asia/Kolkata`) and
`com.careerforge.v2.extras.ProgressService.record(User,String,String,double)`.
All endpoints authenticate through `AuthService.current(request)`.
All mutations run in a service transaction; event records join that transaction.

## Assessments

- `GET /api/assessments`: `{skills:[{skillId,name,questionCount,bestScore,attemptCount}],attempts:[{id,skillId,skillName,score,correct,total,completedAt}]}`.
- `POST /api/assessments/{skillId}/start`: `{id,skillId,skillName,expiresAt,questions:[{id,prompt,options:[string]}]}`. No body required.
- `POST /api/assessments/sessions/{sessionId}/submit`: request `{answers:[{questionId,optionIndex}]}`; response `{attempt:{id,skillId,skillName,score,correct,total,completedAt},feedback:[{questionId,correct,correctOptionIndex,explanation}]}`.

There are three curated MCQs for each of the 18 seeded catalog skill names.
Bank keys use skill names rather than database IDs so seeding order is irrelevant.
Sessions persist the entire question snapshot, including private scoring data,
with random UUID session/question IDs and shuffled question order. A later bank
edit cannot alter scoring for an existing session. Sessions expire 60 minutes
after creation; timestamps are UTC instants. The start response uses a separate
safe DTO and never serializes the stored snapshot or an entity.

Exactly one answer is required for every session question. IDs must match that
session, must not repeat, and option indices are zero-based integers within the
question's option range. Invalid input does not consume the session. Expired
sessions return 410; repeated submissions return 409; missing or other-user
sessions return 404. A pessimistic session row lock serializes concurrent
submissions, and a unique attempt session ID provides a database safeguard.
Score is percentage rounded to one decimal. `bestScore` is null before the first
attempt. Attempts are newest first and provide the complete assessment history.
Completion timestamps are truncated to microseconds before persistence so the
submission and subsequent history responses serialize the same timestamp.
Starting and completing assessments never change self-reported skills or roadmap.

## Planner

- `GET /api/planner?week=YYYY-MM-DD`: `{weekStart,weekEnd,timezone,goals:[{id,title,weekStart,targetMinutes}],tasks:[{id,title,skillId,roadmapStepId,dueDate,estimatedMinutes,status,weekStart}],overdue:[tasks],plannedMinutes,completedMinutes}`.
- `POST /api/planner/goals` and `PUT /api/planner/goals/{id}` accept `{title,weekStart,targetMinutes}` and return a goal DTO.
- `POST /api/planner/tasks` and `PUT /api/planner/tasks/{id}` accept `{title,skillId,roadmapStepId,dueDate,estimatedMinutes,status,weekStart}` and return a task DTO.
- `DELETE /api/planner/goals/{id}` and `DELETE /api/planner/tasks/{id}` return `{message:string}`.

PUT replaces all input fields; optional task link IDs may be null. Titles are
trimmed, nonblank, and at most 255 characters. Week dates normalize to Monday;
weekEnd is Sunday. Without a week query, the current week uses the user's timezone.
PUTs with unchanged normalized fields return the existing DTO without saves,
roadmap updates, or progress events. Comparison includes inferred skill links.
Task dueDate must fall in its normalized week. Goal targets are 1-10080 minutes;
task estimates are 1-1440 minutes. Status is exactly `NOT_STARTED`, `IN_PROGRESS`,
or `COMPLETED`. Planned minutes sum every task in the selected week; completed
minutes sum its completed tasks. Overdue lists all of this user's incomplete
tasks due before today's local date, independently of the selected week.

Goal/task reads and writes are owner-scoped; missing/other-user IDs return 404.
Skill links must resolve to a catalog skill. Roadmap links must belong to this
user and their active career; an explicitly supplied skill must match the step.
A null skillId on a linked task is inferred from that step (including null for a
capstone). Saving a completed linked task calls `CareerService.updateStep` in
the same transaction, preserving its existing self-reported skill side effects.
Reopening or deleting a task does not undo learned skills or roadmap completion.
Task links store a scalar roadmap ID: regeneration can delete a step without
destroying the planner task. Updating a stale linked task requires replacing or
clearing its link; it returns 404 while the stale ID is supplied.

## Progress Events

GETs never create events. Quiz starts/submissions create `ASSESSMENT_STARTED` and
`ASSESSMENT_COMPLETED`. Completion titles include the assessment percentage and
explicitly state that self-reported skills are unchanged. Goal mutations create
`PLANNER_GOAL_CREATED`, `PLANNER_GOAL_UPDATED`, or `PLANNER_GOAL_DELETED`.
Task mutations create `PLANNER_TASK_CREATED`, `PLANNER_TASK_UPDATED`,
`PLANNER_TASK_COMPLETED`, or `PLANNER_TASK_DELETED`. Completion events represent
creation as completed, transition to completed, or a changed roadmap link on a
completed task; other changed PUTs emit update events. No-op PUTs emit nothing.
Every event's readiness is the Number-to-double value of
`data.gap(user,user.careerId).get("readiness")`, never the assessment score.

## Parent Migration DDL

MySQL 8 DDL below matches explicit entity table/column names and Hibernate's
snake-case mapping for public fields. IDs reference existing `users` and `skills`.
Use these four tables in the parent's migration; the parent owns timezone and
progress-event migrations. Hibernate development auto-DDL can create the same
tables on H2; production migrations should use the MySQL DDL.

```sql
CREATE TABLE quiz_sessions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    question_set LONGTEXT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    submitted BOOLEAN NOT NULL,
    CONSTRAINT fk_quiz_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_quiz_skill FOREIGN KEY (skill_id) REFERENCES skills(id)
);

CREATE TABLE assessment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    score DOUBLE NOT NULL,
    correct INT NOT NULL,
    total INT NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_attempt_session UNIQUE (session_id),
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_attempt_skill FOREIGN KEY (skill_id) REFERENCES skills(id)
);

CREATE TABLE planner_goals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    week_start DATE NOT NULL,
    target_minutes INT NOT NULL,
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE planner_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    skill_id BIGINT NULL,
    roadmap_step_id BIGINT NULL,
    due_date DATE NOT NULL,
    estimated_minutes INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    week_start DATE NOT NULL,
    CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_task_skill FOREIGN KEY (skill_id) REFERENCES skills(id)
);
```

`session_id` has a uniqueness constraint but no entity association or foreign
key; attempts remain independently readable if expired sessions are purged.
There is intentionally no roadmap foreign key for the regeneration behavior
described above. All entity associations explicitly use EAGER fetching.

## Validation Handoff

Isolated HTTP smoke checks passed for all 18 quizzes: safe question DTOs, correct
grading, invalid/duplicate/foreign answer rejection, repeat-submission rejection,
history persistence, and unchanged self-reported skills. Planner checks passed
for CRUD, owner scoping, Monday normalization, input limits, weekly totals,
prior-week overdue tasks, and linked completion through `updateStep`. Event
checks verified assessment score/readiness separation and no events from GETs.
The temporary in-memory smoke server was stopped.

The existing `LearningRequestIntegrationTest` is ready for the parent's single
coordinated build. An attempted Maven test run overlapped the parent's build and
failed during Spring configuration discovery in the shared target directory;
that run finished and no further agent builds will run. No test files were edited.
