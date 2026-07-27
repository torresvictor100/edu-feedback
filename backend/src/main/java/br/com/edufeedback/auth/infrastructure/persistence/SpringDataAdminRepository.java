package br.com.edufeedback.auth.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAdminRepository extends JpaRepository<AdminJpaEntity, UUID> {

    Optional<AdminJpaEntity> findByEmail(String email);
}
