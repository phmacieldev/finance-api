CREATE TABLE user_enterprises (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    enterprise_id UUID        NOT NULL REFERENCES enterprise(id) ON DELETE CASCADE,
    role          VARCHAR(50) NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_enterprise UNIQUE (user_id, enterprise_id)
);

CREATE INDEX idx_ue_user       ON user_enterprises(user_id);
CREATE INDEX idx_ue_enterprise ON user_enterprises(enterprise_id);

-- Seed memberships from existing user records
INSERT INTO user_enterprises (user_id, enterprise_id, role)
SELECT id, enterprise_id, role::VARCHAR
FROM   users
WHERE  enterprise_id IS NOT NULL
ON CONFLICT (user_id, enterprise_id) DO NOTHING;

-- Add enterprise context to refresh tokens (needed for multi-company JWT refresh)
ALTER TABLE refresh_tokens
    ADD COLUMN enterprise_id UUID REFERENCES enterprise(id);

UPDATE refresh_tokens rt
SET    enterprise_id = u.enterprise_id
FROM   users u
WHERE  rt.user_id = u.id
  AND  u.enterprise_id IS NOT NULL;
