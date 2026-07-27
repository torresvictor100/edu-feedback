package br.com.edufeedback.functions.avaliacao.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class AvaliacaoPanacheRepository implements PanacheRepository<AvaliacaoPanacheEntity> {
}
