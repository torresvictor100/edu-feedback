package br.com.edufeedback.functions.avaliacao.domain;

import java.util.List;

/**
 * Porta do domínio — implementada em infrastructure/persistence via Panache,
 * sem dependência de framework neste pacote. Só de leitura: o schema da
 * tabela "avaliacoes" é de propriedade do Serviço A.
 */
public interface AvaliacaoRepository {

    List<Avaliacao> listarTodasOrdenadoPorCriadoEm();
}
