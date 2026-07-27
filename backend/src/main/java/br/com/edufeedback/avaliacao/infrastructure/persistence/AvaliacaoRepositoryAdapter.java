package br.com.edufeedback.avaliacao.infrastructure.persistence;

import br.com.edufeedback.avaliacao.domain.Avaliacao;
import br.com.edufeedback.avaliacao.domain.AvaliacaoRepository;
import org.springframework.stereotype.Component;

@Component
class AvaliacaoRepositoryAdapter implements AvaliacaoRepository {

    private final SpringDataAvaliacaoRepository springDataAvaliacaoRepository;

    AvaliacaoRepositoryAdapter(SpringDataAvaliacaoRepository springDataAvaliacaoRepository) {
        this.springDataAvaliacaoRepository = springDataAvaliacaoRepository;
    }

    @Override
    public Avaliacao salvar(Avaliacao avaliacao) {
        AvaliacaoJpaEntity entity = new AvaliacaoJpaEntity(
                avaliacao.getDescricao(), avaliacao.getNota(), avaliacao.getUrgencia());
        AvaliacaoJpaEntity salva = springDataAvaliacaoRepository.save(entity);
        return paraDomain(salva);
    }

    private static Avaliacao paraDomain(AvaliacaoJpaEntity entity) {
        return Avaliacao.reconstituir(
                entity.getId(), entity.getDescricao(), entity.getNota(), entity.getUrgencia(), entity.getCriadoEm());
    }
}
