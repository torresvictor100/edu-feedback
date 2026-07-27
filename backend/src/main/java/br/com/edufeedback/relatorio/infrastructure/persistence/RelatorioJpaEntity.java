package br.com.edufeedback.relatorio.infrastructure.persistence;

import br.com.edufeedback.relatorio.domain.StatusRelatorio;
import br.com.edufeedback.relatorio.domain.TipoRelatorio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidade só de leitura no Serviço A: quem grava relatórios "AGENDADO" é o
 * endpoint interno do Serviço B, que escreve diretamente na mesma tabela.
 */
@Entity
@Table(name = "relatorios")
public class RelatorioJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoRelatorio tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusRelatorio status;

    @Column(name = "solicitado_em", nullable = false)
    private Instant solicitadoEm;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String conteudo;

    protected RelatorioJpaEntity() {
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
