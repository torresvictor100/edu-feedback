package br.com.edufeedback.relatorio.dto;

import br.com.edufeedback.relatorio.Relatorio;
import java.time.Instant;
import java.util.UUID;

public record RelatorioResponse(
        UUID id,
        String tipo,
        String status,
        Instant solicitadoEm,
        Instant concluidoEm,
        String conteudo) {

    public static RelatorioResponse de(Relatorio relatorio) {
        return new RelatorioResponse(
                relatorio.getId(),
                relatorio.getTipo().name(),
                relatorio.getStatus().name(),
                relatorio.getSolicitadoEm(),
                relatorio.getConcluidoEm(),
                relatorio.getConteudo());
    }
}
