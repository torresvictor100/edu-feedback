CREATE TABLE avaliacoes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    descricao TEXT NOT NULL,
    nota INTEGER NOT NULL CHECK (nota >= 0 AND nota <= 10),
    urgencia VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_avaliacoes_criado_em ON avaliacoes (criado_em);
CREATE INDEX idx_avaliacoes_urgencia ON avaliacoes (urgencia);
