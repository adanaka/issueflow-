CREATE TABLE comment_mentions (
    comment_id BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    PRIMARY KEY (comment_id, user_id),
    CONSTRAINT fk_cm_comment FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_user    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE
);
