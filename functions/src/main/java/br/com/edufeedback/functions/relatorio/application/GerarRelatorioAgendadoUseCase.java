package br.com.edufeedback.functions.relatorio.application;

import br.com.edufeedback.functions.avaliacao.domain.Avaliacao;
import br.com.edufeedback.functions.avaliacao.domain.AvaliacaoRepository;
import br.com.edufeedback.functions.relatorio.domain.Agregados;
import br.com.edufeedback.functions.relatorio.domain.AvaliacaoResumo;
import br.com.edufeedback.functions.relatorio.domain.Relatorio;
import br.com.edufeedback.functions.relatorio.domain.RelatorioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Regra de negócio do relatório agendado — chamada pelo endpoint interno
 * {@code RelatorioAgendadoResource}, que por sua vez é acionado pelo gatilho
 * Timer nativo (ver ADR-006 em docs/DECISIONS.md).
 */
@ApplicationScoped
public class GerarRelatorioAgendadoUseCase {

    private static final DateTimeFormatter FORMATO_DIA =
            DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    @Inject
    AvaliacaoRepository avaliacaoRepository;

    @Inject
    RelatorioRepository relatorioRepository;

    @Transactional
    public Agregados gerarRelatorioAgendado() {
        List<Avaliacao> avaliacoes = avaliacaoRepository.listarTodasOrdenadoPorCriadoEm();

        Agregados agregados = calcularAgregados(avaliacoes);
        relatorioRepository.salvar(Relatorio.novoAgendado(agregados));

        return agregados;
    }

    private Agregados calcularAgregados(List<Avaliacao> avaliacoes) {
        double mediaNota = avaliacoes.stream().mapToInt(Avaliacao::getNota).average().orElse(0);
        long total = avaliacoes.size();

        Map<String, Long> porDia = avaliacoes.stream().collect(Collectors.groupingBy(
                a -> FORMATO_DIA.format(a.getCriadoEm()),
                LinkedHashMap::new,
                Collectors.counting()));

        Map<String, Long> porUrgencia = avaliacoes.stream().collect(Collectors.groupingBy(
                Avaliacao::getUrgencia,
                LinkedHashMap::new,
                Collectors.counting()));

        List<AvaliacaoResumo> resumos = avaliacoes.stream()
                .map(a -> new AvaliacaoResumo(a.getDescricao(), a.getUrgencia(), a.getCriadoEm()))
                .toList();

        return new Agregados(mediaNota, total, porDia, porUrgencia, resumos, Instant.now());
    }
}
