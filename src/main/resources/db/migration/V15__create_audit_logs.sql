CREATE TABLE audit_logs (
    id            UUID         NOT NULL PRIMARY KEY,
    user_id       UUID,
    user_email    VARCHAR(100),
    enterprise_id UUID,
    action        VARCHAR(50)  NOT NULL,
    entity_type   VARCHAR(50),
    entity_id     VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_audit_enterprise ON audit_logs (enterprise_id);
CREATE INDEX idx_audit_user       ON audit_logs (user_id);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at);
