-- Remove legacy project table and foreign key from older installs.

ALTER TABLE IF EXISTS requirement
    DROP CONSTRAINT IF EXISTS requirement_project_id_fkey;

DROP TABLE IF EXISTS project;
