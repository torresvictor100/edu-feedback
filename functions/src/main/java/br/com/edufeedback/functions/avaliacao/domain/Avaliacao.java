package br.com.edufeedback.functions.avaliacao.domain;

import java.time.Instant;
import java.util.UUID;

public class Avaliacao {

    private final UUID id;
    private final String descricao;
    private final Integer nota;
    private final String urgencia;
    private final Instant criadoEm;

    public Avaliacao(UUID id, String descricao, Integer nota, String urgencia, Instant criadoEm) {
        this.id = id;
        this.descricao = descricao;
        this.nota = nota;
        this.urgencia = urgencia;
        this.criadoEm = criadoEm;
    }

    public UUID getId() {
        return id;
    }

    public String getDescricao() {
        return descricao;
    }

    public Integer getNota() {
        return nota;
    }

    public String getUrgencia() {
        return urgencia;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
