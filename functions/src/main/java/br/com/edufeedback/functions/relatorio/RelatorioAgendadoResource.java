package br.com.edufeedback.functions.relatorio;

import br.com.edufeedback.functions.shared.Agregados;
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
 * pelo gatilho Timer nativo {@code RelatorioAgendadoTrigger}. Única
 * responsabilidade: gerar o relatório periódico — toda a lógica de negócio
 * vive em {@link RelatorioService} (CDI, Panache).
 */
@Path("/internal/relatorio-agendado")
public class RelatorioAgendadoResource {

    @Inject
    RelatorioService relatorioService;

    @Inject
    InternalSecretValidator internalSecretValidator;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response gerar(@HeaderParam("X-Internal-Secret") String segredo) {
        if (!internalSecretValidator.valido(segredo)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Agregados agregados = relatorioService.gerarRelatorioAgendado();
        return Response.ok(agregados).build();
    }
}
