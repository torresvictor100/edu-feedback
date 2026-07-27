package br.com.edufeedback.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtTokenGeradorTest {

    private final JwtTokenGerador jwtTokenGerador = new JwtTokenGerador(
            "test-secret-value-test-secret-value-test-secret", 3_600_000L);

    @Test
    void deveGerarTokenValidoContendoOEmail() {
        String token = jwtTokenGerador.gerarToken("admin@edufeedback.local");

        assertThat(jwtTokenGerador.tokenValido(token)).isTrue();
        assertThat(jwtTokenGerador.extrairEmail(token)).isEqualTo("admin@edufeedback.local");
    }

    @Test
    void deveConsiderarTokenInvalidoQuandoAlterado() {
        String token = jwtTokenGerador.gerarToken("admin@edufeedback.local");
        String tokenAdulterado = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtTokenGerador.tokenValido(tokenAdulterado)).isFalse();
    }
}
