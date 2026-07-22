package br.com.edufeedback.functions.queue;

import br.com.edufeedback.functions.shared.Agregados;
import br.com.edufeedback.functions.shared.AgregadosJsonSerializer;
import br.com.edufeedback.functions.shared.EmailSender;
import br.com.edufeedback.functions.shared.JdbcRelatorioDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Função Queue Trigger — única responsabilidade: processar um pedido de
 * relatório sob demanda enfileirado pela função HTTP (SolicitarRelatorioResource),
 * gerar o relatório de fato e avisar os administradores por e-mail quando ficar pronto.
 *
 * Não roda dentro do CDI do Quarkus (ver ADR-004 em docs/DECISIONS.md) —
 * usa JdbcRelatorioDao diretamente.
 */
public class ProcessarRelatorioFunction {

    private final JdbcRelatorioDao dao = new JdbcRelatorioDao();
    private final AgregadosJsonSerializer serializer = new AgregadosJsonSerializer();
    private final EmailSender emailSender = new EmailSender();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FunctionName("ProcessarRelatorio")
    public void run(
            @QueueTrigger(
                    name = "message",
                    queueName = "solicitacoes-relatorio",
                    connection = "AZURE_STORAGE_CONNECTION_STRING") String message,
            final ExecutionContext context) {

        context.getLogger().info("Processando solicitação de relatório: " + message);

        UUID relatorioId;
        try (Connection conn = dao.abrirConexao()) {
            relatorioId = extrairRelatorioId(message);

            Agregados agregados = dao.calcularAgregados(conn);
            String conteudoJson = serializer.paraJson(agregados);
            dao.concluirRelatorio(conn, relatorioId, conteudoJson);

            List<String> emailsAdmins = dao.buscarEmailsAdmins(conn);
            emailSender.enviar(
                    emailsAdmins,
                    "Relatório solicitado está pronto",
                    "O relatório " + relatorioId + " foi processado e já pode ser consultado.");

            context.getLogger().info("Relatório " + relatorioId + " concluído.");
        } catch (Exception e) {
            context.getLogger().severe("Falha ao processar relatório sob demanda: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private UUID extrairRelatorioId(String message) throws Exception {
        Map<?, ?> payload = objectMapper.readValue(message, Map.class);
        return UUID.fromString((String) payload.get("relatorioId"));
    }
}
