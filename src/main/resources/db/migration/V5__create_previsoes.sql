CREATE TABLE previsao (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    enterprise_id    UUID          NOT NULL REFERENCES enterprise(id),
    descricao        VARCHAR(255)  NOT NULL,
    tipo             VARCHAR(10)   NOT NULL,
    valor            DECIMAL(15,2) NOT NULL,
    frequencia       VARCHAR(20)   NOT NULL,
    data_inicio      DATE          NOT NULL,
    data_fim         DATE,
    dia_recorrencia  INTEGER,
    categoria_id     UUID          REFERENCES categoria(id),
    ativa            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP     NOT NULL
);

CREATE INDEX idx_previsao_enterprise ON previsao(enterprise_id);
