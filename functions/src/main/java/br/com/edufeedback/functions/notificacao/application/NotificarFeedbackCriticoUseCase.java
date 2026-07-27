package br.com.edufeedback.functions.notificacao.application;

import br.com.edufeedback.functions.admin.domain.AdminRepository;
import br.com.edufeedback.functions.notificacao.domain.EmailSender;
import br.com.edufeedback.functions.notificacao.domain.FeedbackCritico;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Regra de negócio da notificação de feedback crítico — chamada pelo endpoint
 * interno {@code FeedbackCriticoResource}, que por sua vez é acionado pelo
 * gatilho de fila (ver ADR-006/ADR-007 em docs/DECISIONS.md).
 */
@ApplicationScoped
public class NotificarFeedbackCriticoUseCase {

    @Inject
    AdminRepository adminRepository;

    @Inject
    EmailSender emailSender;

    public void notificar(FeedbackCritico feedbackCritico) {
        List<String> emailsAdmins = adminRepository.listarEmails();

        String corpo = "Um feedback crítico foi recebido:\n\n"
                + "Descrição: " + feedbackCritico.descricao() + "\n"
                + "Urgência: " + feedbackCritico.urgencia() + "\n"
                + "Data de envio: " + feedbackCritico.dataEnvio() + "\n\n"
                + "Verifique o quanto antes.";

        emailSender.enviar(emailsAdmins, "Feedback crítico recebido", corpo);
    }
}
