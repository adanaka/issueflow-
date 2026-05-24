CREATE TABLE ticket_dependencies (
    ticket_id     BIGINT NOT NULL,
    blocked_by_id BIGINT NOT NULL,
    PRIMARY KEY (ticket_id, blocked_by_id),
    CONSTRAINT fk_td_ticket      FOREIGN KEY (ticket_id)     REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_td_blocked_by  FOREIGN KEY (blocked_by_id) REFERENCES tickets(id) ON DELETE CASCADE
);

CREATE INDEX idx_td_ticket_id     ON ticket_dependencies (ticket_id);
CREATE INDEX idx_td_blocked_by_id ON ticket_dependencies (blocked_by_id);
