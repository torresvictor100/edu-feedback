package br.com.edufeedback.relatorio;

import br.com.edufeedback.relatorio.dto.RelatorioResponse;
import br.com.edufeedback.shared.exception.RecursoNaoEncontradoException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/relatorios")
public class RelatorioController {

    private final RelatorioRepository relatorioRepository;

    public RelatorioController(RelatorioRepository relatorioRepository) {
        this.relatorioRepository = relatorioRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RelatorioResponse> buscarPorId(@PathVariable UUID id) {
        Relatorio relatorio = relatorioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Relatório não encontrado: " + id));
        return ResponseEntity.ok(RelatorioResponse.de(relatorio));
    }
}
