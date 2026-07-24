package br.com.edufeedback.functions.notificacao;

import br.com.edufeedback.functions.shared.InternalHttpCaller;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import java.net.http.HttpResponse;

/**
 * Gatilho Queue nativo — única responsabilidade: receber a mensagem da fila
 * "notificacoes-criticas" e repassar (sem alteração) para o endpoint interno do
 * Quarkus ({@code FeedbackCriticoResource}), que concentra toda a lógica de
 * negócio (ver ADR-006 em docs/DECISIONS.md).
 *
 * Classe fina, sem CDI — o Quarkus HTTP (via quarkus-azure-functions-http) e os
 * gatilhos nativos (Timer/Queue) coexistem no mesmo módulo, mas não compartilham
 * o container de injeção de dependência.
 */
public class FeedbackCriticoTrigger {

    private final InternalHttpCaller caller = new InternalHttpCaller();

    @FunctionName("FeedbackCritico")
    public void run(
            @QueueTrigger(
                    name = "message",
                    queueName = "notificacoes-criticas",
                    connection = "AZURE_STORAGE_CONNECTION_STRING") String message,
            final ExecutionContext context) {

        context.getLogger().info("Feedback crítico recebido, repassando para o endpoint interno...");

        try {
            HttpResponse<String> resposta = caller.post("/internal/feedback-critico", message);
            if (resposta.statusCode() >= 200 && resposta.statusCode() < 300) {
                context.getLogger().info("Notificação de feedback crítico processada com sucesso.");
            } else {
                context.getLogger().severe("Endpoint interno retornou status " + resposta.statusCode()
                        + ": " + resposta.body());
                throw new IllegalStateException("Falha ao notificar feedback crítico: HTTP " + resposta.statusCode());
            }
        } catch (Exception e) {
            context.getLogger().severe("Falha ao chamar o endpoint interno de feedback crítico: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
