package br.com.edufeedback.functions.shared;

import java.time.Instant;
import java.util.Map;

/**
 * Dados do relatório conforme o contrato do enunciado do desafio: descrição
 * (média/contagens), urgência, data de envio, quantidade por dia e por urgência.
 */
public record Agregados(
        double mediaNota,
        long totalAvaliacoes,
        Map<String, Long> quantidadePorDia,
        Map<String, Long> quantidadePorUrgencia,
        Instant geradoEm) {
}
