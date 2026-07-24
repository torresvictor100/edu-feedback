package br.com.edufeedback.functions.timer;

import br.com.edufeedback.functions.shared.InternalHttpCaller;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import java.net.http.HttpResponse;

/**
 * Gatilho Timer nativo — única responsabilidade: disparar no horário
 * configurado (RELATORIO_AGENDADO_CRON) e repassar para o endpoint interno do
 * Quarkus ({@code RelatorioAgendadoResource}), que concentra toda a lógica de
 * negócio (ver ADR-006 em docs/DECISIONS.md).
 *
 * Classe fina, sem CDI — o Quarkus HTTP (via quarkus-azure-functions-http) e os
 * gatilhos nativos (Timer/Queue) coexistem no mesmo módulo, mas não compartilham
 * o container de injeção de dependência.
 */
public class RelatorioAgendadoTrigger {

    private final InternalHttpCaller caller = new InternalHttpCaller();

    @FunctionName("RelatorioAgendado")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "%RELATORIO_AGENDADO_CRON%") String timerInfo,
            final ExecutionContext context) {

        context.getLogger().info("Disparando geração de relatório agendado...");

        try {
            HttpResponse<String> resposta = caller.post("/internal/relatorio-agendado", null);
            if (resposta.statusCode() >= 200 && resposta.statusCode() < 300) {
                context.getLogger().info("Relatório agendado gerado com sucesso.");
            } else {
                context.getLogger().severe("Endpoint interno retornou status " + resposta.statusCode()
                        + ": " + resposta.body());
                throw new IllegalStateException("Falha ao gerar relatório agendado: HTTP " + resposta.statusCode());
            }
        } catch (Exception e) {
            context.getLogger().severe("Falha ao chamar o endpoint interno de relatório agendado: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
