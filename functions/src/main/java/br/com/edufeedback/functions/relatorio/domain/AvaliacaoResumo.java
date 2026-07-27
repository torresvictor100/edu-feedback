package br.com.edufeedback.functions.relatorio.domain;

import java.time.Instant;

/**
 * Um item do relatório, no formato exigido pelo enunciado: descrição, urgência
 * e data de envio de cada avaliação.
 */
public record AvaliacaoResumo(String descricao, String urgencia, Instant dataEnvio) {
}
