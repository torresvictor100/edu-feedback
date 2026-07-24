package br.com.edufeedback.functions.notificacao;

import static io.restassured.RestAssured.given;

import br.com.edufeedback.functions.admin.AdminEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FeedbackCriticoResourceTest {

    private static final String SEGREDO = "changeit-local-dev-internal-secret";
    private static final String PAYLOAD = """
            {
              "avaliacaoId": "11111111-1111-1111-1111-111111111111",
              "descricao": "Não entendi nada da aula",
              "urgencia": "CRITICA",
              "dataEnvio": "2026-07-22T12:00:00Z"
            }
            """;

    @BeforeEach
    void seedAdmin() {
        QuarkusTransaction.requiringNew().run(() -> {
            AdminEntity admin = new AdminEntity();
            admin.id = UUID.randomUUID();
            admin.email = "admin@edufeedback.local";
            admin.persist();
        });
    }

    @Test
    void deveRejeitarSemSegredoInterno() {
        given()
                .contentType(ContentType.JSON)
                .body(PAYLOAD)
                .when().post("/internal/feedback-critico")
                .then().statusCode(401);
    }

    @Test
    void deveNotificarComSegredoValido() {
        given()
                .header("X-Internal-Secret", SEGREDO)
                .contentType(ContentType.JSON)
                .body(PAYLOAD)
                .when().post("/internal/feedback-critico")
                .then().statusCode(200);
    }
}
