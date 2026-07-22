CREATE TABLE relatorios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    solicitado_em TIMESTAMP NOT NULL DEFAULT now(),
    concluido_em TIMESTAMP,
    conteudo JSONB
);

CREATE INDEX idx_relatorios_status ON relatorios (status);
