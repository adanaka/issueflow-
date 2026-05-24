-- Migrate projects from boolean deleted flag to timestamp
ALTER TABLE projects ADD COLUMN deleted_at TIMESTAMP;
UPDATE projects SET deleted_at = NOW() WHERE deleted = TRUE;
ALTER TABLE projects DROP COLUMN deleted;

-- Migrate tickets from boolean deleted flag to timestamp
ALTER TABLE tickets ADD COLUMN deleted_at TIMESTAMP;
UPDATE tickets SET deleted_at = NOW() WHERE deleted = TRUE;
ALTER TABLE tickets DROP COLUMN deleted;
