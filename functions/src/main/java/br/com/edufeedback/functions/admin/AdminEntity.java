package br.com.edufeedback.functions.admin;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

/**
 * Mapeamento só de leitura da tabela "admins" — o schema é de propriedade do
 * Serviço A (Flyway); esta entidade nunca é usada para escrever.
 */
@Entity
@Table(name = "admins")
public class AdminEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String email;

    public static List<String> listarEmails() {
        return AdminEntity.<AdminEntity>listAll().stream().map(admin -> admin.email).toList();
    }
}
