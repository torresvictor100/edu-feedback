package br.com.edufeedback.avaliacao.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAvaliacaoRepository extends JpaRepository<AvaliacaoJpaEntity, UUID> {
}
