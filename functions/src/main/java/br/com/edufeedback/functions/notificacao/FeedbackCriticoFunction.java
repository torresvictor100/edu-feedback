package br.com.edufeedback.functions.notificacao;

import br.com.edufeedback.functions.shared.EmailSender;
import br.com.edufeedback.functions.shared.JdbcRelatorioDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * Função Queue Trigger — única responsabilidade: notificar os administradores
 * por e-mail quando o Serviço A publica uma avaliação crítica (nota <= limite)
 * na fila "notificacoes-criticas". O corpo do e-mail traz os 3 dados exigidos
 * pelo enunciado: descrição, urgência e data de envio.
 *
 * Uma das 2 funções serverless do Serviço B (ver ADR-005 em docs/DECISIONS.md) —
 * sem framework de aplicação, usa JdbcRelatorioDao apenas para buscar os e-mails
 * dos administradores.
 */
public class FeedbackCriticoFunction {

    private final JdbcRelatorioDao dao = new JdbcRelatorioDao();
    private final EmailSender emailSender = new EmailSender();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FunctionName("FeedbackCritico")
    public void run(
            @QueueTrigger(
                    name = "message",
                    queueName = "notificacoes-criticas",
                    connection = "AZURE_STORAGE_CONNECTION_STRING") String message,
            final ExecutionContext context) {

        context.getLogger().info("Feedback crítico recebido: " + message);

        try (Connection conn = dao.abrirConexao()) {
            Map<?, ?> payload = objectMapper.readValue(message, Map.class);
            String descricao = String.valueOf(payload.get("descricao"));
            String urgencia = String.valueOf(payload.get("urgencia"));
            String dataEnvio = String.valueOf(payload.get("dataEnvio"));

            List<String> emailsAdmins = dao.buscarEmailsAdmins(conn);
            String corpo = "Um feedback crítico foi recebido:\n\n"
                    + "Descrição: " + descricao + "\n"
                    + "Urgência: " + urgencia + "\n"
                    + "Data de envio: " + dataEnvio + "\n\n"
                    + "Verifique o quanto antes.";

            emailSender.enviar(emailsAdmins, "Feedback crítico recebido", corpo);

            context.getLogger().info("Notificação de feedback crítico enviada para " + emailsAdmins.size()
                    + " administrador(es).");
        } catch (Exception e) {
            context.getLogger().severe("Falha ao notificar feedback crítico: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
