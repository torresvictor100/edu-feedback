package br.com.edufeedback.functions.admin.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class AdminPanacheRepository implements PanacheRepository<AdminPanacheEntity> {
}
