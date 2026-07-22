package br.com.edufeedback;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Container Postgres compartilhado ("singleton container pattern"): iniciado
 * uma única vez via bloco estático e nunca parado explicitamente — o Ryuk do
 * Testcontainers encerra ao final da JVM. Isso evita que o container seja
 * iniciado/parado repetidamente entre as classes de teste que estendem esta base.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("edufeedback")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("azure.storage.connection-string", () -> "");
        registry.add("jwt.secret", () -> "test-secret-value-test-secret-value-test-secret");
    }
}
