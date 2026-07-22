package br.com.edufeedback.avaliacao;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica na fila "notificacoes-criticas" quando uma avaliação crítica é recebida.
 * A função serverless (Serviço B) consome essa fila e envia o e-mail.
 */
@Component
public class NotificacaoCriticaPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoCriticaPublisher.class);

    private final QueueClient queueClient;

    public NotificacaoCriticaPublisher(
            @Value("${azure.storage.connection-string}") String connectionString,
            @Value("${azure.storage.queue.notificacoes-criticas}") String queueName) {
        this.queueClient = construirCliente(connectionString, queueName);
    }

    private QueueClient construirCliente(String connectionString, String queueName) {
        if (connectionString == null || connectionString.isBlank()) {
            log.warn("AZURE_STORAGE_CONNECTION_STRING não configurada — notificações críticas "
                    + "serão apenas logadas, não enfileiradas. Configure a variável em produção.");
            return null;
        }
        QueueClient client = new QueueClientBuilder()
                .connectionString(connectionString)
                .queueName(queueName)
                .buildClient();
        client.createIfNotExists();
        return client;
    }

    public void publicar(Avaliacao avaliacao) {
        String mensagem = String.format(
                "{\"avaliacaoId\":\"%s\",\"nota\":%d,\"criadoEm\":\"%s\"}",
                avaliacao.getId(), avaliacao.getNota(), avaliacao.getCriadoEm());

        if (queueClient == null) {
            log.info("[dev] notificação crítica não enfileirada (sem Azure Storage): {}", mensagem);
            return;
        }

        String mensagemBase64 = Base64.getEncoder().encodeToString(mensagem.getBytes());
        queueClient.sendMessage(mensagemBase64);
    }
}
