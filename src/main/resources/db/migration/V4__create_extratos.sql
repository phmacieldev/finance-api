CREATE TABLE extrato (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    enterprise_id   UUID           NOT NULL REFERENCES enterprise(id),
    data            DATE           NOT NULL,
    tipo_pagamento  VARCHAR(50),
    razao_social    VARCHAR(255),
    cpf_cnpj        VARCHAR(20),
    valor           DECIMAL(15,2)  NOT NULL,
    saldo           DECIMAL(15,2),
    categoria_id    UUID           REFERENCES categoria(id),
    conciliado      BOOLEAN        NOT NULL DEFAULT FALSE,
    mes             INTEGER        NOT NULL,
    ano             INTEGER        NOT NULL,
    import_hash     VARCHAR(64)    NOT NULL,
    importado_em    TIMESTAMP      NOT NULL,
    CONSTRAINT uq_extrato_hash UNIQUE (enterprise_id, import_hash)
);

CREATE INDEX idx_extrato_enterprise_data    ON extrato(enterprise_id, data);
CREATE INDEX idx_extrato_enterprise_mes_ano ON extrato(enterprise_id, mes, ano);
CREATE INDEX idx_extrato_categoria          ON extrato(categoria_id);
