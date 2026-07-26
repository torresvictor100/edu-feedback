package br.com.edufeedback.functions.shared;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Valida o cabeçalho {@code X-Internal-Secret} nos endpoints "/internal/*".
 * Essas rotas só existem para os 2 Container Apps Jobs (gatilho fino, ver
 * ADR-007 em docs/DECISIONS.md) chamarem — nunca para clientes externos. O
 * Container App deste módulo não tem ingress externo, mas a checagem continua
 * como segunda camada de defesa. Sem o segredo correto, o endpoint deve
 * responder 401 antes de executar qualquer lógica de negócio.
 */
@ApplicationScoped
public class InternalSecretValidator {

    @ConfigProperty(name = "internal.trigger-secret")
    String segredoEsperado;

    public boolean valido(String recebido) {
        return recebido != null && recebido.equals(segredoEsperado);
    }
}
