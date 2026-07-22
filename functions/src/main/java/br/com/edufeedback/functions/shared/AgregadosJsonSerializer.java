package br.com.edufeedback.functions.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Lógica pura (sem I/O) de serialização do relatório — separada do acesso a
 * dados para ser testável isoladamente.
 */
public class AgregadosJsonSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public String paraJson(Agregados agregados) {
        try {
            return objectMapper.writeValueAsString(agregados);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar agregados do relatório", e);
        }
    }
}
