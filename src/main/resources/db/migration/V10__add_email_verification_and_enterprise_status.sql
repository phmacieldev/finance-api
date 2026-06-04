-- Email verification fields
ALTER TABLE users ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN token_verificacao VARCHAR(64);

-- Mark all existing users as already verified (retroactive)
UPDATE users SET email_verificado = TRUE;

-- Enterprise approval status
ALTER TABLE enterprise ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ATIVA';

-- Mark all existing enterprises as active
UPDATE enterprise SET status = 'ATIVA';
