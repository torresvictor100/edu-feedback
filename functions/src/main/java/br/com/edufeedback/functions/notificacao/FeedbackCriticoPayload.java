package br.com.edufeedback.functions.notificacao;

import java.time.Instant;

/**
 * Corpo publicado pelo Serviço A na fila "notificacoes-criticas" e repassado
 * sem alteração pelo gatilho nativo até este endpoint interno.
 */
public record FeedbackCriticoPayload(String avaliacaoId, String descricao, String urgencia, Instant dataEnvio) {
}
