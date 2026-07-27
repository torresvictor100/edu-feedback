package br.com.edufeedback.functions.avaliacao.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeamento só de leitura da tabela "avaliacoes" — o schema é de propriedade
 * do Serviço A (Flyway); esta entidade nunca é usada para escrever. Acessada
 * só via {@link AvaliacaoPanacheRepository} (padrão repositório do Panache),
 * nunca por chamada estática de active record, para a camada de aplicação
 * não depender de Panache.
 */
@Entity
@Table(name = "avaliacoes")
public class AvaliacaoPanacheEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String descricao;

    @Column(nullable = false)
    public Integer nota;

    @Column(nullable = false)
    public String urgencia;

    @Column(name = "criado_em", nullable = false)
    public Instant criadoEm;
}
