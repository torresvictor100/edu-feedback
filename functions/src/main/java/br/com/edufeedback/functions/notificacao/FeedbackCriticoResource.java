package br.com.edufeedback.functions.notificacao;

import br.com.edufeedback.functions.admin.AdminEntity;
import br.com.edufeedback.functions.shared.EmailService;
import br.com.edufeedback.functions.shared.InternalSecretValidator;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Endpoint interno (protegido por {@code InternalSecretValidator}) acionado
 * pelo Container Apps Job de evento "job-feedback-critico" (escalado pela
 * profundidade da fila "notificacoes-criticas", ver ADR-007 em
 * docs/DECISIONS.md). Única responsabilidade: notificar os administradores
 * por e-mail com os dados exigidos pelo enunciado (descrição, urgência, data
 * de envio).
 */
@Path("/internal/feedback-critico")
public class FeedbackCriticoResource {

    @Inject
    EmailService emailService;

    @Inject
    InternalSecretValidator internalSecretValidator;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response notificar(@HeaderParam("X-Internal-Secret") String segredo, FeedbackCriticoPayload payload) {
        if (!internalSecretValidator.valido(segredo)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<String> emailsAdmins = AdminEntity.listarEmails();

        String corpo = "Um feedback crítico foi recebido:\n\n"
                + "Descrição: " + payload.descricao() + "\n"
                + "Urgência: " + payload.urgencia() + "\n"
                + "Data de envio: " + payload.dataEnvio() + "\n\n"
                + "Verifique o quanto antes.";

        emailService.enviar(emailsAdmins, "Feedback crítico recebido", corpo);

        return Response.ok().build();
    }
}
