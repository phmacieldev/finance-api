ALTER TABLE enterprise
    ADD COLUMN tipo_pessoa VARCHAR(10) NOT NULL DEFAULT 'JURIDICA',
    ADD COLUMN cpf         VARCHAR(14);

ALTER TABLE enterprise ALTER COLUMN cnpj DROP NOT NULL;

CREATE UNIQUE INDEX uk_enterprise_cpf ON enterprise(cpf) WHERE cpf IS NOT NULL;
