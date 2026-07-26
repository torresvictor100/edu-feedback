package br.com.edufeedback.functions.notificacao;

import java.time.Instant;

/**
 * Corpo publicado pelo Serviço A na fila "notificacoes-criticas" e repassado
 * sem alteração pelo Container Apps Job "job-feedback-critico" até este
 * endpoint interno (ver ADR-007 em docs/DECISIONS.md).
 */
public record FeedbackCriticoPayload(String avaliacaoId, String descricao, String urgencia, Instant dataEnvio) {
}
