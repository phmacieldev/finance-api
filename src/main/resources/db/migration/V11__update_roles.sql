-- Migra o antigo papel ADMIN (primeiro usuário da empresa) para CEO
UPDATE users SET role = 'CEO' WHERE role = 'ADMIN';
