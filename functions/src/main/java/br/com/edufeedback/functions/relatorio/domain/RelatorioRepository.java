package br.com.edufeedback.functions.relatorio.domain;

/**
 * Porta do domínio — implementada em infrastructure/persistence via Panache,
 * sem dependência de framework neste pacote.
 */
public interface RelatorioRepository {

    void salvar(Relatorio relatorio);
}
