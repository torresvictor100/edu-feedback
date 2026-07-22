package br.com.edufeedback.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-value-test-secret-value-test-secret", 3_600_000L);

    @Test
    void deveGerarTokenValidoContendoOEmail() {
        String token = jwtService.gerarToken("admin@edufeedback.local");

        assertThat(jwtService.tokenValido(token)).isTrue();
        assertThat(jwtService.extrairEmail(token)).isEqualTo("admin@edufeedback.local");
    }

    @Test
    void deveConsiderarTokenInvalidoQuandoAlterado() {
        String token = jwtService.gerarToken("admin@edufeedback.local");
        String tokenAdulterado = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtService.tokenValido(tokenAdulterado)).isFalse();
    }
}
