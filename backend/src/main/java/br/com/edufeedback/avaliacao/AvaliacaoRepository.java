package br.com.edufeedback.avaliacao;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {
}
