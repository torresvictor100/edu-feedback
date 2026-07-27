package br.com.edufeedback.functions.notificacao.infrastructure.email;

import br.com.edufeedback.functions.notificacao.domain.EmailSender;
import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.communication.email.models.EmailSendStatus;
import com.azure.core.util.polling.SyncPoller;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Implementa a porta {@link EmailSender} via Azure Communication Services,
 * usado pelo endpoint interno de notificação de feedback crítico.
 */
@ApplicationScoped
public class AzureEmailSender implements EmailSender {

    private static final Logger log = Logger.getLogger(AzureEmailSender.class);
    private static final Duration TIMEOUT_ENVIO = Duration.ofSeconds(30);

    private final String connectionString;
    private final String remetente;

    public AzureEmailSender(
            @ConfigProperty(name = "azure.communication-services.connection-string") Optional<String> connectionString,
            @ConfigProperty(name = "azure.communication-services.sender-address") Optional<String> remetente) {
        this.connectionString = connectionString.orElse("");
        this.remetente = remetente.orElse("");
    }

    @Override
    public void enviar(List<String> destinatarios, String assunto, String corpo) {
        if (connectionString.isBlank() || destinatarios.isEmpty()) {
            log.infof("[dev] e-mail não enviado (sem Azure Communication Services configurado): %s -> %s",
                    assunto, destinatarios);
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

            SyncPoller<EmailSendResult, EmailSendResult> poller = client.beginSend(mensagem);
            EmailSendResult resultado = poller.waitForCompletion(TIMEOUT_ENVIO).getValue();

            if (resultado.getStatus() != EmailSendStatus.SUCCEEDED) {
                log.warnf("Falha ao enviar e-mail para %s: status=%s", destinatario, resultado.getStatus());
            }
        }
    }
}
