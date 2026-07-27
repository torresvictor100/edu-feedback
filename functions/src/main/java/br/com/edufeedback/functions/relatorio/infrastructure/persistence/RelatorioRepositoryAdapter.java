package br.com.edufeedback.functions.relatorio.infrastructure.persistence;

import br.com.edufeedback.functions.relatorio.domain.Relatorio;
import br.com.edufeedback.functions.relatorio.domain.RelatorioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class RelatorioRepositoryAdapter implements RelatorioRepository {

    private final AgregadosJsonSerializer agregadosJsonSerializer = new AgregadosJsonSerializer();

    @Inject
    RelatorioPanacheRepository relatorioPanacheRepository;

    @Override
    public void salvar(Relatorio relatorio) {
        RelatorioPanacheEntity entity = new RelatorioPanacheEntity();
        entity.id = relatorio.getId();
        entity.tipo = relatorio.getTipo();
        entity.status = relatorio.getStatus();
        entity.solicitadoEm = relatorio.getSolicitadoEm();
        entity.concluidoEm = relatorio.getConcluidoEm();
        entity.conteudo = agregadosJsonSerializer.paraJson(relatorio.getAgregados());
        relatorioPanacheRepository.persist(entity);
    }
}
