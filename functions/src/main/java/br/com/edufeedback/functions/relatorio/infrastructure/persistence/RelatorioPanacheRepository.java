package br.com.edufeedback.functions.relatorio.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class RelatorioPanacheRepository implements PanacheRepository<RelatorioPanacheEntity> {
}
