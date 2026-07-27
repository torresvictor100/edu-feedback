package br.com.edufeedback.functions.relatorio.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.edufeedback.functions.relatorio.domain.Agregados;
import br.com.edufeedback.functions.relatorio.domain.AvaliacaoResumo;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgregadosJsonSerializerTest {

    private final AgregadosJsonSerializer serializer = new AgregadosJsonSerializer();

    @Test
    void deveSerializarAgregadosComoJsonValido() {
        Agregados agregados = new Agregados(
                7.5,
                4,
                Map.of("2026-07-20", 2L, "2026-07-21", 2L),
                Map.of("NORMAL", 3L, "CRITICA", 1L),
                List.of(new AvaliacaoResumo("Aula ruim", "CRITICA", Instant.parse("2026-07-20T10:00:00Z"))),
                Instant.parse("2026-07-22T12:00:00Z"));

        String json = serializer.paraJson(agregados);

        assertThat(json).contains("\"mediaNota\":7.5");
        assertThat(json).contains("\"totalAvaliacoes\":4");
        assertThat(json).contains("\"CRITICA\":1");
        assertThat(json).contains("\"descricao\":\"Aula ruim\"");
    }
}
