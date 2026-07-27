package br.com.edufeedback.functions.notificacao.infrastructure.web;

import br.com.edufeedback.functions.notificacao.application.NotificarFeedbackCriticoUseCase;
import br.com.edufeedback.functions.notificacao.domain.FeedbackCritico;
import br.com.edufeedback.functions.shared.InternalSecretValidator;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint interno (protegido por {@code InternalSecretValidator}) acionado
 * pelo Container Apps Job de evento "job-feedback-critico" (escalado pela
 * profundidade da fila "notificacoes-criticas", ver ADR-007 em
 * docs/DECISIONS.md). Única responsabilidade: notificar os administradores
 * por e-mail com os dados exigidos pelo enunciado (descrição, urgência, data
 * de envio) — toda a lógica de negócio vive em
 * {@link NotificarFeedbackCriticoUseCase}.
 */
@Path("/internal/feedback-critico")
public class FeedbackCriticoResource {

    @Inject
    NotificarFeedbackCriticoUseCase notificarFeedbackCriticoUseCase;

    @Inject
    InternalSecretValidator internalSecretValidator;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response notificar(@HeaderParam("X-Internal-Secret") String segredo, FeedbackCriticoPayload payload) {
        if (!internalSecretValidator.valido(segredo)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        notificarFeedbackCriticoUseCase.notificar(
                new FeedbackCritico(payload.descricao(), payload.urgencia(), payload.dataEnvio()));

        return Response.ok().build();
    }
}
