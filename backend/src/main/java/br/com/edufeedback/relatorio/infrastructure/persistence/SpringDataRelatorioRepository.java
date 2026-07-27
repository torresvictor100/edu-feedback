package br.com.edufeedback.relatorio.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRelatorioRepository extends JpaRepository<RelatorioJpaEntity, UUID> {
}
