package br.com.edufeedback.avaliacao.domain;

import java.time.Instant;
import java.util.UUID;

public class Avaliacao {

    private final UUID id;
    private final String descricao;
    private final Integer nota;
    private final Urgencia urgencia;
    private final Instant criadoEm;

    private Avaliacao(UUID id, String descricao, Integer nota, Urgencia urgencia, Instant criadoEm) {
        this.id = id;
        this.descricao = descricao;
        this.nota = nota;
        this.urgencia = urgencia;
        this.criadoEm = criadoEm;
    }

    public static Avaliacao registrar(String descricao, Integer nota, Urgencia urgencia) {
        return new Avaliacao(null, descricao, nota, urgencia, null);
    }

    public static Avaliacao reconstituir(UUID id, String descricao, Integer nota, Urgencia urgencia, Instant criadoEm) {
        return new Avaliacao(id, descricao, nota, urgencia, criadoEm);
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

    public Urgencia getUrgencia() {
        return urgencia;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
