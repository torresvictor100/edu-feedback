package br.com.edufeedback.avaliacao.infrastructure.web.dto;

import br.com.edufeedback.avaliacao.domain.Avaliacao;
import java.time.Instant;
import java.util.UUID;

public record AvaliacaoResponse(
        UUID id,
        String descricao,
        Integer nota,
        String urgencia,
        Instant criadoEm) {

    public static AvaliacaoResponse de(Avaliacao avaliacao) {
        return new AvaliacaoResponse(
                avaliacao.getId(),
                avaliacao.getDescricao(),
                avaliacao.getNota(),
                avaliacao.getUrgencia().name(),
                avaliacao.getCriadoEm());
    }
}
