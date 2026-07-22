package br.com.edufeedback.avaliacao;

import br.com.edufeedback.avaliacao.dto.AvaliacaoRequest;
import br.com.edufeedback.avaliacao.dto.AvaliacaoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato definido pelo enunciado do desafio: POST /avaliação, com corpo
 * {"descricao": string, "nota": int (0 a 10)}. Endpoint público, sem autenticação.
 */
@RestController
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    public AvaliacaoController(AvaliacaoService avaliacaoService) {
        this.avaliacaoService = avaliacaoService;
    }

    @PostMapping(value = "/avaliação", produces = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AvaliacaoResponse> registrar(@Valid @RequestBody AvaliacaoRequest request) {
        Avaliacao avaliacao = avaliacaoService.registrar(request.descricao(), request.nota());
        return ResponseEntity.status(HttpStatus.CREATED).body(AvaliacaoResponse.de(avaliacao));
    }
}
