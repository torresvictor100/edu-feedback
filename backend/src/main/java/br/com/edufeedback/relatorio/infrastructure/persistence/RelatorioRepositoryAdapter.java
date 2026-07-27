package br.com.edufeedback.relatorio.infrastructure.persistence;

import br.com.edufeedback.relatorio.domain.Relatorio;
import br.com.edufeedback.relatorio.domain.RelatorioRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class RelatorioRepositoryAdapter implements RelatorioRepository {

    private final SpringDataRelatorioRepository springDataRelatorioRepository;

    RelatorioRepositoryAdapter(SpringDataRelatorioRepository springDataRelatorioRepository) {
        this.springDataRelatorioRepository = springDataRelatorioRepository;
    }

    @Override
    public Optional<Relatorio> buscarPorId(UUID id) {
        return springDataRelatorioRepository.findById(id).map(RelatorioRepositoryAdapter::paraDomain);
    }

    @Override
    public List<Relatorio> listarTodosOrdenadoPorSolicitadoEmDesc() {
        return springDataRelatorioRepository
                .findAll(Sort.by(Sort.Direction.DESC, "solicitadoEm"))
                .stream()
                .map(RelatorioRepositoryAdapter::paraDomain)
                .toList();
    }

    private static Relatorio paraDomain(RelatorioJpaEntity entity) {
        return new Relatorio(
                entity.getId(),
                entity.getTipo(),
                entity.getStatus(),
                entity.getSolicitadoEm(),
                entity.getConcluidoEm(),
                entity.getConteudo());
    }
}
