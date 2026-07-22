package br.com.edufeedback.functions.shared;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dados do relatório conforme o contrato do enunciado do desafio: lista de
 * avaliações (descrição, urgência, data de envio de cada uma), quantidade por
 * dia e quantidade por urgência, além da média geral de notas.
 */
public record Agregados(
        double mediaNota,
        long totalAvaliacoes,
        Map<String, Long> quantidadePorDia,
        Map<String, Long> quantidadePorUrgencia,
        List<AvaliacaoResumo> avaliacoes,
        Instant geradoEm) {
}
