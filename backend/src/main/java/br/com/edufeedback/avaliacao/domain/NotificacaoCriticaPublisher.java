package br.com.edufeedback.avaliacao.domain;

/**
 * Porta do domínio — implementada em infrastructure/messaging (fila Azure
 * Storage), sem dependência do SDK Azure neste pacote.
 */
public interface NotificacaoCriticaPublisher {

    void publicar(Avaliacao avaliacao);
}
