package br.com.edufeedback.functions.relatorio;

import br.com.edufeedback.functions.relatorio.dto.SolicitarRelatorioResponse;
import br.com.edufeedback.functions.shared.JwtValidator;
import br.com.edufeedback.functions.shared.QueuePublisher;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Função HTTP (Azure Functions via quarkus-azure-functions-http). Único endpoint
 * responsável por receber a solicitação de relatório sob demanda: grava o pedido
 * como "PROCESSANDO" e enfileira o processamento — a geração de fato acontece na
 * função de fila (ProcessarRelatorioFunction), pois pode demorar.
 */
@Path("/relatorios/solicitacoes")
public class SolicitarRelatorioResource {

    @Inject
    JwtValidator jwtValidator;

    @Inject
    QueuePublisher queuePublisher;

    @ConfigProperty(name = "azure.storage.queue.solicitacoes-relatorio")
    String filaSolicitacoes;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response solicitar(@HeaderParam("Authorization") String authorizationHeader) {
        if (!jwtValidator.tokenValidoParaAdmin(authorizationHeader)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        RelatorioEntity relatorio = RelatorioEntity.novaSolicitacao();
        relatorio.persist();

        String mensagem = String.format("{\"relatorioId\":\"%s\"}", relatorio.id);
        queuePublisher.publicar(filaSolicitacoes, mensagem);

        return Response.accepted(new SolicitarRelatorioResponse(relatorio.id, relatorio.status.name())).build();
    }
}
