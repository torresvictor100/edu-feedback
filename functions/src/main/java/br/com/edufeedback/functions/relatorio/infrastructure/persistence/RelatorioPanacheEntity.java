package br.com.edufeedback.functions.relatorio.infrastructure.persistence;

import br.com.edufeedback.functions.relatorio.domain.StatusRelatorio;
import br.com.edufeedback.functions.relatorio.domain.TipoRelatorio;
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

/**
 * Entidade de escrita da tabela "relatorios" — o schema é de propriedade do
 * Serviço A (Flyway); esta entidade nunca cria/altera a tabela, só insere
 * linhas. Acessada só via {@link RelatorioPanacheRepository}.
 */
@Entity
@Table(name = "relatorios")
public class RelatorioPanacheEntity extends PanacheEntityBase {

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
}
