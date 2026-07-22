package br.com.edufeedback.avaliacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "avaliacoes")
public class Avaliacao {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private Integer nota;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Urgencia urgencia;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Avaliacao() {
    }

    public Avaliacao(String descricao, Integer nota, Urgencia urgencia) {
        this.descricao = descricao;
        this.nota = nota;
        this.urgencia = urgencia;
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
