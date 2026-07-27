package br.com.edufeedback.functions.notificacao.domain;

import java.util.List;

/**
 * Porta do domínio — implementada em infrastructure/email (Azure
 * Communication Services), sem dependência do SDK Azure neste pacote.
 */
public interface EmailSender {

    void enviar(List<String> destinatarios, String assunto, String corpo);
}
