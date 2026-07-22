CREATE TABLE admins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(100) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

-- Admin seed para desenvolvimento local. Senha: admin123
-- Trocar em produção antes do primeiro deploy real.
INSERT INTO admins (email, senha_hash)
VALUES ('admin@edufeedback.local', '$2b$10$9yWBB5oTrSIzhrM/C4psM.Y4W/doKBBQzhJNANCVEUCgBwO8jvlU6');
