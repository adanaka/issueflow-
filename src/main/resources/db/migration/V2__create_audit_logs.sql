CREATE TABLE audit_logs (
    id           BIGSERIAL   PRIMARY KEY,
    action       VARCHAR(20) NOT NULL,
    entity_type  VARCHAR(20) NOT NULL,
    entity_id    BIGINT      NOT NULL,
    actor        VARCHAR(10) NOT NULL,
    performed_by BIGINT,
    details      TEXT,
    timestamp    TIMESTAMP   NOT NULL
);

CREATE INDEX idx_audit_action       ON audit_logs (action);
CREATE INDEX idx_audit_entity_type  ON audit_logs (entity_type);
CREATE INDEX idx_audit_entity_id    ON audit_logs (entity_id);
CREATE INDEX idx_audit_actor        ON audit_logs (actor);
CREATE INDEX idx_audit_performed_by ON audit_logs (performed_by);
