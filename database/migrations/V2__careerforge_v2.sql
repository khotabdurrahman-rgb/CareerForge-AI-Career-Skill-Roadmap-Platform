-- Additive V2 upgrade: run once through Flyway after V1 or a version-1 baseline.
ALTER TABLE users ADD COLUMN timezone VARCHAR(255) NOT NULL DEFAULT 'Asia/Kolkata';
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE account_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_account_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE mentor_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content VARCHAR(12000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_mentor_message_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE mentor_usage (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    request_count INT NOT NULL,
    CONSTRAINT uq_mentor_user_date UNIQUE (user_id, usage_date),
    CONSTRAINT fk_mentor_usage_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE quiz_sessions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    question_set ${largeTextType} NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    submitted BOOLEAN NOT NULL,
    CONSTRAINT fk_quiz_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_quiz_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
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
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_attempt_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE planner_goals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    week_start DATE NOT NULL,
    target_minutes INT NOT NULL,
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE planner_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    skill_id BIGINT,
    roadmap_step_id BIGINT,
    due_date DATE NOT NULL,
    estimated_minutes INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    week_start DATE NOT NULL,
    CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_task_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

-- Scalar historic references intentionally have no foreign keys: roadmap/project/
-- career deletion must not erase saved plans, recommendations, or progress.
CREATE TABLE progress_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    readiness DOUBLE NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    career_id BIGINT,
    CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_progress_readiness CHECK (readiness >= 0 AND readiness <= 100)
);

CREATE INDEX ix_progress_user_time ON progress_events (user_id, occurred_at, id);

CREATE TABLE saved_recommendations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recommendation_id VARCHAR(100) NOT NULL,
    saved BOOLEAN NOT NULL,
    added_project_id BIGINT,
    CONSTRAINT uk_recommendation_user UNIQUE (user_id, recommendation_id),
    CONSTRAINT fk_saved_recommendation_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE resume_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    fields_json ${largeTextType} NOT NULL,
    CONSTRAINT uk_resume_user UNIQUE (user_id),
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
