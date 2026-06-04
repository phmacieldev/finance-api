CREATE TABLE saldo_anterior (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    enterprise_id UUID          NOT NULL REFERENCES enterprise(id),
    mes           INTEGER       NOT NULL,
    ano           INTEGER       NOT NULL,
    valor         DECIMAL(15,2) NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,
    CONSTRAINT uq_saldo_anterior UNIQUE (enterprise_id, mes, ano)
);
