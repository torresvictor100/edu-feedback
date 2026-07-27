package br.com.edufeedback.functions.relatorio.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Só o Serviço B grava relatórios — o Serviço A apenas consulta. A
 * serialização de {@code agregados} para JSON é um detalhe da persistência,
 * não do domínio (ver {@code infrastructure/persistence}).
 */
public class Relatorio {

    private final UUID id;
    private final TipoRelatorio tipo;
    private final StatusRelatorio status;
    private final Instant solicitadoEm;
    private final Instant concluidoEm;
    private final Agregados agregados;

    private Relatorio(
            UUID id,
            TipoRelatorio tipo,
            StatusRelatorio status,
            Instant solicitadoEm,
            Instant concluidoEm,
            Agregados agregados) {
        this.id = id;
        this.tipo = tipo;
        this.status = status;
        this.solicitadoEm = solicitadoEm;
        this.concluidoEm = concluidoEm;
        this.agregados = agregados;
    }

    public static Relatorio novoAgendado(Agregados agregados) {
        Instant agora = Instant.now();
        return new Relatorio(UUID.randomUUID(), TipoRelatorio.AGENDADO, StatusRelatorio.CONCLUIDO, agora, agora, agregados);
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

    public Agregados getAgregados() {
        return agregados;
    }
}
