-- Flyway migration: Initial schema for myReqEng Requirements Engineering Tool
-- Creates tables for Requirement (tree structure) and Link (traceability)

-- Requirement: hierarchical tree per project. parent_id = 0 for root nodes
CREATE TABLE IF NOT EXISTS requirement (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL,
    identifier  VARCHAR(100) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    parent_id   BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_requirement_project ON requirement(project_id);
CREATE INDEX IF NOT EXISTS idx_requirement_parent ON requirement(parent_id);
CREATE INDEX IF NOT EXISTS idx_requirement_project_parent ON requirement(project_id, parent_id);

-- Link: traceability links between requirements (source -> target)
CREATE TABLE IF NOT EXISTS link (
    id     BIGSERIAL PRIMARY KEY,
    source BIGINT NOT NULL REFERENCES requirement(id) ON DELETE CASCADE,
    target BIGINT NOT NULL REFERENCES requirement(id) ON DELETE CASCADE,
    CONSTRAINT uk_link_source_target UNIQUE (source, target)
);

CREATE INDEX IF NOT EXISTS idx_link_source ON link(source);
CREATE INDEX IF NOT EXISTS idx_link_target ON link(target);
