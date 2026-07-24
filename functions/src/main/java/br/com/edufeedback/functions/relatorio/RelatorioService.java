package br.com.edufeedback.functions.relatorio;

import br.com.edufeedback.functions.avaliacao.AvaliacaoEntity;
import br.com.edufeedback.functions.shared.Agregados;
import br.com.edufeedback.functions.shared.AgregadosJsonSerializer;
import br.com.edufeedback.functions.shared.AvaliacaoResumo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Regra de negócio do relatório agendado — chamada pelo endpoint interno
 * {@link RelatorioAgendadoResource}, que por sua vez é acionado pelo gatilho
 * Timer nativo (ver ADR-006 em docs/DECISIONS.md).
 */
@ApplicationScoped
public class RelatorioService {

    private static final DateTimeFormatter FORMATO_DIA =
            DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final AgregadosJsonSerializer serializer = new AgregadosJsonSerializer();

    @Transactional
    public Agregados gerarRelatorioAgendado() {
        List<AvaliacaoEntity> avaliacoes = AvaliacaoEntity.<AvaliacaoEntity>listAll()
                .stream()
                .sorted(Comparator.comparing(a -> a.criadoEm))
                .toList();

        Agregados agregados = calcularAgregados(avaliacoes);
        String conteudoJson = serializer.paraJson(agregados);
        RelatorioEntity.novoRelatorioAgendado(conteudoJson).persist();

        return agregados;
    }

    private Agregados calcularAgregados(List<AvaliacaoEntity> avaliacoes) {
        double mediaNota = avaliacoes.stream().mapToInt(a -> a.nota).average().orElse(0);
        long total = avaliacoes.size();

        Map<String, Long> porDia = avaliacoes.stream().collect(Collectors.groupingBy(
                a -> FORMATO_DIA.format(a.criadoEm),
                LinkedHashMap::new,
                Collectors.counting()));

        Map<String, Long> porUrgencia = avaliacoes.stream().collect(Collectors.groupingBy(
                a -> a.urgencia,
                LinkedHashMap::new,
                Collectors.counting()));

        List<AvaliacaoResumo> resumos = avaliacoes.stream()
                .map(a -> new AvaliacaoResumo(a.descricao, a.urgencia, a.criadoEm))
                .toList();

        return new Agregados(mediaNota, total, porDia, porUrgencia, resumos, Instant.now());
    }
}
