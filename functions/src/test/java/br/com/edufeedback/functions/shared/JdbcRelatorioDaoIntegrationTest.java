package br.com.edufeedback.functions.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Testa o DAO usado pelas 2 funções (Timer e Queue) contra um Postgres real via
 * Testcontainers — schema mínimo criado aqui, isolado do banco do Serviço A.
 */
class JdbcRelatorioDaoIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("edufeedback")
            .withUsername("postgres")
            .withPassword("postgres");

    private static JdbcRelatorioDao dao;

    @BeforeAll
    static void iniciarContainer() {
        postgres.start();
        System.setProperty("test.postgres.host", postgres.getHost());
        System.setProperty("test.postgres.port", String.valueOf(postgres.getMappedPort(5432)));

        dao = new JdbcRelatorioDao() {
            @Override
            public Connection abrirConexao() throws java.sql.SQLException {
                return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            }
        };
    }

    @AfterAll
    static void pararContainer() {
        postgres.stop();
    }

    @BeforeEach
    void recriarSchema() throws Exception {
        try (Connection conn = dao.abrirConexao(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS avaliacoes, relatorios, admins");
            stmt.execute("CREATE TABLE avaliacoes (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "nota INTEGER NOT NULL, urgencia VARCHAR(20) NOT NULL, criado_em TIMESTAMP NOT NULL DEFAULT now())");
            stmt.execute("CREATE TABLE relatorios (id UUID PRIMARY KEY, tipo VARCHAR(20) NOT NULL, "
                    + "status VARCHAR(20) NOT NULL, solicitado_em TIMESTAMP NOT NULL, concluido_em TIMESTAMP, conteudo JSONB)");
            stmt.execute("CREATE TABLE admins (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), email VARCHAR(255) NOT NULL)");
        }
    }

    @Test
    void deveCalcularMediaETotaisPorDiaEUrgencia() throws Exception {
        try (Connection conn = dao.abrirConexao(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO avaliacoes (nota, urgencia) VALUES (9, 'NORMAL')");
            stmt.execute("INSERT INTO avaliacoes (nota, urgencia) VALUES (2, 'CRITICA')");
            stmt.execute("INSERT INTO avaliacoes (nota, urgencia) VALUES (7, 'NORMAL')");
        }

        try (Connection conn = dao.abrirConexao()) {
            Agregados agregados = dao.calcularAgregados(conn);

            assertThat(agregados.totalAvaliacoes()).isEqualTo(3);
            assertThat(agregados.mediaNota()).isEqualTo(6.0);
            assertThat(agregados.quantidadePorUrgencia()).containsEntry("NORMAL", 2L).containsEntry("CRITICA", 1L);
        }
    }

    @Test
    void deveInserirEDepoisConcluirRelatorio() throws Exception {
        UUID id = UUID.randomUUID();

        try (Connection conn = dao.abrirConexao()) {
            dao.inserirRelatorioAgendado(conn, id, "{\"mediaNota\":5.0}");
        }

        try (Connection conn = dao.abrirConexao(); Statement stmt = conn.createStatement();
                var rs = stmt.executeQuery("SELECT status FROM relatorios WHERE id = '" + id + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo("CONCLUIDO");
        }
    }

    @Test
    void deveBuscarEmailsDosAdmins() throws Exception {
        try (Connection conn = dao.abrirConexao(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO admins (email) VALUES ('admin@edufeedback.local')");
        }

        try (Connection conn = dao.abrirConexao()) {
            List<String> emails = dao.buscarEmailsAdmins(conn);
            assertThat(emails).containsExactly("admin@edufeedback.local");
        }
    }
}
