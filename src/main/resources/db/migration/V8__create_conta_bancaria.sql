CREATE TABLE conta_bancaria (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    enterprise_id UUID         NOT NULL REFERENCES enterprise(id),
    nome          VARCHAR(100) NOT NULL,
    banco         VARCHAR(60)  NOT NULL,
    tipo          VARCHAR(20)  NOT NULL DEFAULT 'CORRENTE',
    ativa         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conta_bancaria_enterprise ON conta_bancaria(enterprise_id);
