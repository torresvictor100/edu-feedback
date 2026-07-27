package br.com.edufeedback.relatorio.application;

import br.com.edufeedback.relatorio.domain.Relatorio;
import br.com.edufeedback.relatorio.domain.RelatorioRepository;
import br.com.edufeedback.shared.exception.RecursoNaoEncontradoException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsultarRelatorioUseCase {

    private final RelatorioRepository relatorioRepository;

    public ConsultarRelatorioUseCase(RelatorioRepository relatorioRepository) {
        this.relatorioRepository = relatorioRepository;
    }

    public Relatorio buscarPorId(UUID id) {
        return relatorioRepository.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Relatório não encontrado: " + id));
    }

    public List<Relatorio> listar() {
        return relatorioRepository.listarTodosOrdenadoPorSolicitadoEmDesc();
    }
}
