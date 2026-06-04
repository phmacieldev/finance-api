ALTER TABLE users
    ADD COLUMN token_reset_senha VARCHAR(64),
    ADD COLUMN token_reset_expiracao TIMESTAMP;
