package br.com.edufeedback.functions.relatorio.infrastructure.persistence;

import br.com.edufeedback.functions.relatorio.domain.Agregados;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Serialização de {@link Agregados} para o formato armazenado na coluna
 * {@code conteudo} (jsonb) — detalhe de persistência, isolado do domínio.
 */
class AgregadosJsonSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    String paraJson(Agregados agregados) {
        try {
            return objectMapper.writeValueAsString(agregados);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar agregados do relatório", e);
        }
    }
}
