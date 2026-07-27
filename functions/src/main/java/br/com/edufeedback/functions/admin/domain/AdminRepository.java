package br.com.edufeedback.functions.admin.domain;

import java.util.List;

/**
 * Porta do domínio — implementada em infrastructure/persistence via Panache,
 * sem dependência de framework neste pacote. Só de leitura: o schema da
 * tabela "admins" é de propriedade do Serviço A.
 */
public interface AdminRepository {

    List<String> listarEmails();
}
