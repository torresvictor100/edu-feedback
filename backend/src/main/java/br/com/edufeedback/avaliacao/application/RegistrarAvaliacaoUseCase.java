package br.com.edufeedback.avaliacao.application;

import br.com.edufeedback.avaliacao.domain.Avaliacao;
import br.com.edufeedback.avaliacao.domain.AvaliacaoRepository;
import br.com.edufeedback.avaliacao.domain.NotificacaoCriticaPublisher;
import br.com.edufeedback.avaliacao.domain.Urgencia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RegistrarAvaliacaoUseCase {

    private final AvaliacaoRepository avaliacaoRepository;
    private final NotificacaoCriticaPublisher notificacaoCriticaPublisher;
    private final int notaCriticaLimite;

    public RegistrarAvaliacaoUseCase(
            AvaliacaoRepository avaliacaoRepository,
            NotificacaoCriticaPublisher notificacaoCriticaPublisher,
            @Value("${app.nota-critica-limite}") int notaCriticaLimite) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.notificacaoCriticaPublisher = notificacaoCriticaPublisher;
        this.notaCriticaLimite = notaCriticaLimite;
    }

    public Avaliacao registrar(String descricao, Integer nota) {
        Urgencia urgencia = nota <= notaCriticaLimite ? Urgencia.CRITICA : Urgencia.NORMAL;
        Avaliacao avaliacao = Avaliacao.registrar(descricao, nota, urgencia);
        Avaliacao salva = avaliacaoRepository.salvar(avaliacao);

        if (urgencia == Urgencia.CRITICA) {
            notificacaoCriticaPublisher.publicar(salva);
        }

        return salva;
    }
}
