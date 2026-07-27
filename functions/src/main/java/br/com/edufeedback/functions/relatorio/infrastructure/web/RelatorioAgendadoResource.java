package br.com.edufeedback.functions.relatorio.infrastructure.web;

import br.com.edufeedback.functions.relatorio.application.GerarRelatorioAgendadoUseCase;
import br.com.edufeedback.functions.relatorio.domain.Agregados;
import br.com.edufeedback.functions.shared.InternalSecretValidator;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint interno (protegido por {@code InternalSecretValidator}) acionado
 * pelo Container Apps Job de agendamento "job-relatorio-agendado" (Schedule
 * trigger, ver ADR-007 em docs/DECISIONS.md). Única responsabilidade: gerar o
 * relatório periódico — toda a lógica de negócio vive em
 * {@link GerarRelatorioAgendadoUseCase}.
 */
@Path("/internal/relatorio-agendado")
public class RelatorioAgendadoResource {

    @Inject
    GerarRelatorioAgendadoUseCase gerarRelatorioAgendadoUseCase;

    @Inject
    InternalSecretValidator internalSecretValidator;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response gerar(@HeaderParam("X-Internal-Secret") String segredo) {
        if (!internalSecretValidator.valido(segredo)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Agregados agregados = gerarRelatorioAgendadoUseCase.gerarRelatorioAgendado();
        return Response.ok(agregados).build();
    }
}
