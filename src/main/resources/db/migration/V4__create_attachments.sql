CREATE TABLE attachments (
    id           BIGSERIAL    PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size    BIGINT       NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_att_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);

CREATE INDEX idx_att_ticket_id ON attachments (ticket_id);
