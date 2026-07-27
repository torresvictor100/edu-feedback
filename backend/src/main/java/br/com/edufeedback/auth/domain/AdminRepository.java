package br.com.edufeedback.auth.domain;

import java.util.Optional;

/**
 * Porta do domínio — implementada em infrastructure/persistence, sem
 * dependência de Spring Data ou JPA neste pacote.
 */
public interface AdminRepository {

    Optional<Admin> buscarPorEmail(String email);
}
