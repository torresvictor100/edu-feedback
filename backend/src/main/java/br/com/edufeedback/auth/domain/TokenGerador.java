package br.com.edufeedback.auth.domain;

/**
 * Porta do domínio — implementada em infrastructure/security (JWT), sem
 * dependência de biblioteca de token neste pacote.
 */
public interface TokenGerador {

    String gerarToken(String email);
}
