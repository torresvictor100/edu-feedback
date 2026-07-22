package br.com.edufeedback.functions.shared;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Base64;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class QueuePublisher {

    private static final Logger log = Logger.getLogger(QueuePublisher.class);

    private final String connectionString;

    public QueuePublisher(
            @ConfigProperty(name = "azure.storage.connection-string") Optional<String> connectionString) {
        this.connectionString = connectionString.orElse("");
    }

    public void publicar(String queueName, String mensagem) {
        if (connectionString == null || connectionString.isBlank()) {
            log.infof("[dev] mensagem não enfileirada em '%s' (sem Azure Storage): %s", queueName, mensagem);
            return;
        }

        QueueClient client = new QueueClientBuilder()
                .connectionString(connectionString)
                .queueName(queueName)
                .buildClient();
        client.createIfNotExists();

        String mensagemBase64 = Base64.getEncoder().encodeToString(mensagem.getBytes());
        client.sendMessage(mensagemBase64);
    }
}
