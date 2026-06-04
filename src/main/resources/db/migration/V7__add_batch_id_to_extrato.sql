ALTER TABLE extrato ADD COLUMN import_batch_id UUID;

CREATE INDEX idx_extrato_batch ON extrato(import_batch_id);
