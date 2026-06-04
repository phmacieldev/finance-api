-- Permite enterprise_id nulo (PLATFORM_ADMIN não pertence a nenhuma empresa)
ALTER TABLE users ALTER COLUMN enterprise_id DROP NOT NULL;

-- Desvincula os admins da plataforma de qualquer empresa
UPDATE users SET enterprise_id = NULL WHERE role = 'PLATFORM_ADMIN';

-- Remove a empresa "Plataforma — Administração" que não tem mais usuários
DELETE FROM enterprise
WHERE id NOT IN (
    SELECT DISTINCT enterprise_id FROM users WHERE enterprise_id IS NOT NULL
);
