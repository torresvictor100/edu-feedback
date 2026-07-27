package br.com.edufeedback.relatorio.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Só de leitura no Serviço A: quem grava relatórios "AGENDADO" é o endpoint
 * interno do Serviço B, que escreve diretamente na mesma tabela. O Serviço A
 * apenas consulta e repassa {@code conteudo} como JSON bruto, sem interpretá-lo.
 */
public class Relatorio {

    private final UUID id;
    private final TipoRelatorio tipo;
    private final StatusRelatorio status;
    private final Instant solicitadoEm;
    private final Instant concluidoEm;
    private final String conteudo;

    public Relatorio(
            UUID id,
            TipoRelatorio tipo,
            StatusRelatorio status,
            Instant solicitadoEm,
            Instant concluidoEm,
            String conteudo) {
        this.id = id;
        this.tipo = tipo;
        this.status = status;
        this.solicitadoEm = solicitadoEm;
        this.concluidoEm = concluidoEm;
        this.conteudo = conteudo;
    }

    public UUID getId() {
        return id;
    }

    public TipoRelatorio getTipo() {
        return tipo;
    }

    public StatusRelatorio getStatus() {
        return status;
    }

    public Instant getSolicitadoEm() {
        return solicitadoEm;
    }

    public Instant getConcluidoEm() {
        return concluidoEm;
    }

    public String getConteudo() {
        return conteudo;
    }
}
