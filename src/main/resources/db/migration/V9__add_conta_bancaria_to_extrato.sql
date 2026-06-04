ALTER TABLE extrato ADD COLUMN conta_bancaria_id UUID REFERENCES conta_bancaria(id);

CREATE INDEX idx_extrato_conta_bancaria ON extrato(conta_bancaria_id);
