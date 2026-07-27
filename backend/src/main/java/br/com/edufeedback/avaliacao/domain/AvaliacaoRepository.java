package br.com.edufeedback.avaliacao.domain;

/**
 * Porta do domínio — implementada em infrastructure/persistence, sem
 * dependência de Spring Data ou JPA neste pacote.
 */
public interface AvaliacaoRepository {

    Avaliacao salvar(Avaliacao avaliacao);
}
