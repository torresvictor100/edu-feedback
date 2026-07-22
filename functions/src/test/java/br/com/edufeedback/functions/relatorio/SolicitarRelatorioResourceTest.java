package br.com.edufeedback.functions.relatorio;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SolicitarRelatorioResourceTest {

    private static final String JWT_SECRET = "changeit-local-dev-secret-changeit-local-dev-secret";

    @Test
    void deveRejeitarSolicitacaoSemToken() {
        given()
                .when().post("/relatorios/solicitacoes")
                .then().statusCode(401);
    }

    @Test
    void deveAceitarSolicitacaoComTokenAdminValido() {
        String token = gerarTokenAdmin();

        given()
                .header("Authorization", "Bearer " + token)
                .when().post("/relatorios/solicitacoes")
                .then()
                .statusCode(202)
                .body("status", equalTo("PROCESSANDO"));
    }

    @Test
    void deveRejeitarTokenSemPapelAdmin() {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("estudante@exemplo.com")
                .claim("role", "ESTUDANTE")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();

        given()
                .header("Authorization", "Bearer " + token)
                .when().post("/relatorios/solicitacoes")
                .then().statusCode(401);
    }

    private String gerarTokenAdmin() {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("admin@edufeedback.local")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }
}
