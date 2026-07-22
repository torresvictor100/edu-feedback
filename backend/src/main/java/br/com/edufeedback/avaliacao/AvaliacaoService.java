package br.com.edufeedback.avaliacao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final NotificacaoCriticaPublisher notificacaoCriticaPublisher;
    private final int notaCriticaLimite;

    public AvaliacaoService(
            AvaliacaoRepository avaliacaoRepository,
            NotificacaoCriticaPublisher notificacaoCriticaPublisher,
            @Value("${app.nota-critica-limite}") int notaCriticaLimite) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.notificacaoCriticaPublisher = notificacaoCriticaPublisher;
        this.notaCriticaLimite = notaCriticaLimite;
    }

    public Avaliacao registrar(String descricao, Integer nota) {
        Urgencia urgencia = nota <= notaCriticaLimite ? Urgencia.CRITICA : Urgencia.NORMAL;
        Avaliacao avaliacao = new Avaliacao(descricao, nota, urgencia);
        Avaliacao salva = avaliacaoRepository.save(avaliacao);

        if (urgencia == Urgencia.CRITICA) {
            notificacaoCriticaPublisher.publicar(salva);
        }

        return salva;
    }
}
