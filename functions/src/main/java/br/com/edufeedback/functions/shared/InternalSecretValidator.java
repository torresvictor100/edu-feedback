package br.com.edufeedback.functions.shared;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Valida o cabeçalho {@code X-Internal-Secret} nos endpoints "/internal/*".
 * Essas rotas só existem para os gatilhos nativos (Timer/Queue) chamarem —
 * nunca para clientes externos — mesmo estando atrás do proxy HTTP público do
 * quarkus-azure-functions-http. Sem o segredo correto, o endpoint deve
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
