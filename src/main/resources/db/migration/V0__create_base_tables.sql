-- Base schema: tables that existed before V1–V5 were introduced.
-- V5 migrates 'deleted BOOLEAN' → 'deleted_at TIMESTAMP' on projects and tickets.

CREATE TABLE users (
    id        BIGSERIAL    PRIMARY KEY,
    username  VARCHAR(50)  NOT NULL UNIQUE,
    email     VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    password  VARCHAR(255),
    role      VARCHAR(20)  NOT NULL
);

CREATE TABLE projects (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    owner_id    BIGINT       NOT NULL REFERENCES users(id),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE tickets (
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'TODO',
    priority    VARCHAR(20)  NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    project_id  BIGINT       NOT NULL REFERENCES projects(id),
    assignee_id BIGINT       REFERENCES users(id),
    due_date    TIMESTAMP,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT
);

CREATE TABLE comments (
    id         BIGSERIAL  PRIMARY KEY,
    content    TEXT       NOT NULL,
    ticket_id  BIGINT     NOT NULL REFERENCES tickets(id),
    author_id  BIGINT     NOT NULL REFERENCES users(id),
    created_at TIMESTAMP  NOT NULL,
    updated_at TIMESTAMP  NOT NULL,
    version    BIGINT
);
