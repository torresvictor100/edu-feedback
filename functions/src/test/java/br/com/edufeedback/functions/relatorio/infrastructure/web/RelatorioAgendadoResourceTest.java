package br.com.edufeedback.functions.relatorio.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import br.com.edufeedback.functions.avaliacao.infrastructure.persistence.AvaliacaoPanacheEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RelatorioAgendadoResourceTest {

    private static final String SEGREDO = "changeit-local-dev-internal-secret";

    @BeforeEach
    void seedAvaliacao() {
        QuarkusTransaction.requiringNew().run(() -> {
            AvaliacaoPanacheEntity avaliacao = new AvaliacaoPanacheEntity();
            avaliacao.id = UUID.randomUUID();
            avaliacao.descricao = "Aula muito boa";
            avaliacao.nota = 9;
            avaliacao.urgencia = "NORMAL";
            avaliacao.criadoEm = Instant.now();
            avaliacao.persist();
        });
    }

    @Test
    void deveRejeitarSemSegredoInterno() {
        given()
                .when().post("/internal/relatorio-agendado")
                .then().statusCode(401);
    }

    @Test
    void deveRejeitarComSegredoIncorreto() {
        given()
                .header("X-Internal-Secret", "segredo-errado")
                .when().post("/internal/relatorio-agendado")
                .then().statusCode(401);
    }

    @Test
    void deveGerarRelatorioComSegredoValido() {
        given()
                .header("X-Internal-Secret", SEGREDO)
                .when().post("/internal/relatorio-agendado")
                .then()
                .statusCode(200)
                .body("totalAvaliacoes", greaterThanOrEqualTo(1));
    }
}
