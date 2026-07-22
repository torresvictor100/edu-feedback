package br.com.edufeedback.functions.relatorio;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
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

@Entity
@Table(name = "relatorios")
public class RelatorioEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TipoRelatorio tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public StatusRelatorio status;

    @Column(name = "solicitado_em", nullable = false)
    public Instant solicitadoEm;

    @Column(name = "concluido_em")
    public Instant concluidoEm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public String conteudo;

    public static RelatorioEntity novaSolicitacao() {
        RelatorioEntity relatorio = new RelatorioEntity();
        relatorio.id = UUID.randomUUID();
        relatorio.tipo = TipoRelatorio.SOB_DEMANDA;
        relatorio.status = StatusRelatorio.PROCESSANDO;
        relatorio.solicitadoEm = Instant.now();
        return relatorio;
    }
}
