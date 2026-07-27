package br.com.edufeedback.functions.notificacao.domain;

import java.time.Instant;

/**
 * Dado de entrada da regra de negócio de notificação — publicado pelo Serviço
 * A na fila "notificacoes-criticas" e repassado pelo gatilho fino até o
 * endpoint interno (ver ADR-007 em docs/DECISIONS.md).
 */
public record FeedbackCritico(String descricao, String urgencia, Instant dataEnvio) {
}
