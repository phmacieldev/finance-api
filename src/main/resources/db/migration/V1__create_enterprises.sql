CREATE TABLE enterprise (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    cnpj        VARCHAR(18)  NOT NULL UNIQUE,
    plan        VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
