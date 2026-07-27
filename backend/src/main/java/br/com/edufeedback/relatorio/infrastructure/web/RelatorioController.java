package br.com.edufeedback.relatorio.infrastructure.web;

import br.com.edufeedback.relatorio.application.ConsultarRelatorioUseCase;
import br.com.edufeedback.relatorio.infrastructure.web.dto.RelatorioResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/relatorios")
public class RelatorioController {

    private final ConsultarRelatorioUseCase consultarRelatorioUseCase;

    public RelatorioController(ConsultarRelatorioUseCase consultarRelatorioUseCase) {
        this.consultarRelatorioUseCase = consultarRelatorioUseCase;
    }

    @GetMapping
    public ResponseEntity<List<RelatorioResponse>> listar() {
        List<RelatorioResponse> relatorios = consultarRelatorioUseCase.listar()
                .stream()
                .map(RelatorioResponse::de)
                .toList();
        return ResponseEntity.ok(relatorios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RelatorioResponse> buscarPorId(@PathVariable UUID id) {
        RelatorioResponse relatorio = RelatorioResponse.de(consultarRelatorioUseCase.buscarPorId(id));
        return ResponseEntity.ok(relatorio);
    }
}
