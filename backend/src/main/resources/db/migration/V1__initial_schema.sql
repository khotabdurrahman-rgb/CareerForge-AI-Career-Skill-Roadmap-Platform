-- V1 baseline schema for H2 (MySQL mode) and MySQL.
-- This script does not drop tables or erase existing records.

CREATE TABLE IF NOT EXISTS skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_skills_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS careers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    PRIMARY KEY (id),
    CONSTRAINT uq_careers_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    course VARCHAR(255),
    college VARCHAR(255),
    current_year VARCHAR(255),
    career_id BIGINT,
    role VARCHAR(255) NOT NULL DEFAULT 'STUDENT',
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS user_skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    level VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_skills UNIQUE (user_id, skill_id),
    CONSTRAINT fk_user_skills_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE IF NOT EXISTS career_skills (
    career_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (career_id, skill_id),
    CONSTRAINT fk_career_skills_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT fk_career_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE IF NOT EXISTS roadmap_steps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    career_id BIGINT NOT NULL,
    skill_id BIGINT,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'NOT_STARTED',
    position INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_roadmap_user_career_title UNIQUE (user_id, career_id, title),
    CONSTRAINT fk_roadmap_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_roadmap_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT fk_roadmap_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    technology VARCHAR(255),
    github_url VARCHAR(1000),
    status VARCHAR(255) DEFAULT 'IN_PROGRESS',
    PRIMARY KEY (id),
    CONSTRAINT fk_projects_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS resources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    skill_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    type VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_resources_skill FOREIGN KEY (skill_id) REFERENCES skills (id)
);


CREATE INDEX ix_roadmap_user_position ON roadmap_steps (user_id, position);
