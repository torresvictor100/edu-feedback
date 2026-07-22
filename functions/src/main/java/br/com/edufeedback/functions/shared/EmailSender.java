package br.com.edufeedback.functions.shared;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import java.util.List;
import java.util.logging.Logger;

/**
 * Envio de e-mail via Azure Communication Services. Usada pelas funções Timer
 * e Queue trigger (não têm acesso ao CDI do Quarkus — ver ADR-004), por isso é
 * uma classe simples, sem anotações de framework.
 */
public class EmailSender {

    private static final Logger log = Logger.getLogger(EmailSender.class.getName());

    private final String connectionString;
    private final String remetente;

    public EmailSender() {
        this.connectionString = getenvOrDefault("AZURE_COMMUNICATION_SERVICES_CONNECTION_STRING", "");
        this.remetente = getenvOrDefault("AZURE_COMMUNICATION_SERVICES_SENDER_ADDRESS", "");
    }

    public void enviar(List<String> destinatarios, String assunto, String corpo) {
        if (connectionString.isBlank() || destinatarios.isEmpty()) {
            log.info("[dev] e-mail não enviado (sem Azure Communication Services configurado): "
                    + assunto + " -> " + destinatarios);
            return;
        }

        EmailClient client = new EmailClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        for (String destinatario : destinatarios) {
            EmailMessage mensagem = new EmailMessage()
                    .setSenderAddress(remetente)
                    .setToRecipients(destinatario)
                    .setSubject(assunto)
                    .setBodyPlainText(corpo);
            client.beginSend(mensagem);
        }
    }

    private static String getenvOrDefault(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }
}
