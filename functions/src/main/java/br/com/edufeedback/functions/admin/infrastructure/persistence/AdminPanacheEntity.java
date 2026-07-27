package br.com.edufeedback.functions.admin.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Mapeamento só de leitura da tabela "admins" — o schema é de propriedade do
 * Serviço A (Flyway); esta entidade nunca é usada para escrever. Acessada só
 * via {@link AdminPanacheRepository} (padrão repositório do Panache), nunca
 * por chamada estática de active record.
 */
@Entity
@Table(name = "admins")
public class AdminPanacheEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String email;
}
