package br.com.edufeedback.functions.avaliacao.infrastructure.persistence;

import br.com.edufeedback.functions.avaliacao.domain.Avaliacao;
import br.com.edufeedback.functions.avaliacao.domain.AvaliacaoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
class AvaliacaoRepositoryAdapter implements AvaliacaoRepository {

    @Inject
    AvaliacaoPanacheRepository avaliacaoPanacheRepository;

    @Override
    public List<Avaliacao> listarTodasOrdenadoPorCriadoEm() {
        return avaliacaoPanacheRepository.listAll(Sort.by("criadoEm"))
                .stream()
                .map(AvaliacaoRepositoryAdapter::paraDomain)
                .toList();
    }

    private static Avaliacao paraDomain(AvaliacaoPanacheEntity entity) {
        return new Avaliacao(entity.id, entity.descricao, entity.nota, entity.urgencia, entity.criadoEm);
    }
}
