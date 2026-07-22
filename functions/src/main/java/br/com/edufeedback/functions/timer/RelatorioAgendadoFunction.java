package br.com.edufeedback.functions.timer;

import br.com.edufeedback.functions.shared.Agregados;
import br.com.edufeedback.functions.shared.AgregadosJsonSerializer;
import br.com.edufeedback.functions.shared.JdbcRelatorioDao;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import java.sql.Connection;
import java.util.UUID;

/**
 * Função Timer Trigger — única responsabilidade: gerar periodicamente um
 * relatório de médias e contagens de avaliações. Periodicidade configurada
 * via app setting RELATORIO_AGENDADO_CRON (formato NCRONTAB do Azure Functions).
 *
 * Não roda dentro do CDI do Quarkus (ver ADR-004 em docs/DECISIONS.md) —
 * usa JdbcRelatorioDao diretamente.
 */
public class RelatorioAgendadoFunction {

    private final JdbcRelatorioDao dao = new JdbcRelatorioDao();
    private final AgregadosJsonSerializer serializer = new AgregadosJsonSerializer();

    @FunctionName("RelatorioAgendado")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "%RELATORIO_AGENDADO_CRON%") String timerInfo,
            final ExecutionContext context) {

        context.getLogger().info("Gerando relatório agendado...");

        try (Connection conn = dao.abrirConexao()) {
            Agregados agregados = dao.calcularAgregados(conn);
            String conteudoJson = serializer.paraJson(agregados);
            dao.inserirRelatorioAgendado(conn, UUID.randomUUID(), conteudoJson);
            context.getLogger().info("Relatório agendado gerado com sucesso: " + agregados.totalAvaliacoes()
                    + " avaliações consideradas.");
        } catch (Exception e) {
            context.getLogger().severe("Falha ao gerar relatório agendado: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
