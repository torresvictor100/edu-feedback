package br.com.edufeedback.relatorio.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta do domínio — implementada em infrastructure/persistence, sem
 * dependência de Spring Data ou JPA neste pacote.
 */
public interface RelatorioRepository {

    Optional<Relatorio> buscarPorId(UUID id);

    List<Relatorio> listarTodosOrdenadoPorSolicitadoEmDesc();
}
