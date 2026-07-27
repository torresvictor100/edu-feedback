package br.com.edufeedback.avaliacao.infrastructure.messaging;

import br.com.edufeedback.avaliacao.domain.Avaliacao;
import br.com.edufeedback.avaliacao.domain.NotificacaoCriticaPublisher;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementa a porta {@link NotificacaoCriticaPublisher}, publicando na fila
 * "notificacoes-criticas" quando uma avaliação crítica é recebida. A função
 * serverless (Serviço B) consome essa fila e envia o e-mail com os dados
 * exigidos pelo enunciado: descrição, urgência e data de envio.
 */
@Component
public class AzureQueueNotificacaoCriticaPublisher implements NotificacaoCriticaPublisher {

    private static final Logger log = LoggerFactory.getLogger(AzureQueueNotificacaoCriticaPublisher.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public AzureQueueNotificacaoCriticaPublisher(
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

    @Override
    public void publicar(Avaliacao avaliacao) {
        String mensagem;
        try {
            mensagem = objectMapper.writeValueAsString(new NotificacaoCriticaPayload(
                    avaliacao.getId().toString(),
                    avaliacao.getDescricao(),
                    avaliacao.getUrgencia().name(),
                    avaliacao.getCriadoEm()));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar notificação crítica", e);
        }

        if (queueClient == null) {
            log.info("[dev] notificação crítica não enfileirada (sem Azure Storage): {}", mensagem);
            return;
        }

        String mensagemBase64 = Base64.getEncoder().encodeToString(mensagem.getBytes());
        queueClient.sendMessage(mensagemBase64);
    }

    private record NotificacaoCriticaPayload(
            String avaliacaoId,
            String descricao,
            String urgencia,
            java.time.Instant dataEnvio) {
    }
}
