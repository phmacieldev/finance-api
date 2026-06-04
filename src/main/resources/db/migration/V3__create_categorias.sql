CREATE TABLE categoria (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    enterprise_id UUID         NOT NULL REFERENCES enterprise(id),
    name          VARCHAR(100) NOT NULL,
    tipo          VARCHAR(20)  NOT NULL,
    dre_categoria VARCHAR(50),
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uq_categoria_name UNIQUE (enterprise_id, name)
);

CREATE INDEX idx_categoria_enterprise ON categoria(enterprise_id);
