package br.com.edufeedback.functions.shared;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Acesso a dados via JDBC simples para as 2 funções do Serviço B (Timer e
 * Queue trigger — ver ADR-005 em docs/DECISIONS.md). O schema é de propriedade
 * do Serviço A (Flyway); esta classe nunca cria ou altera tabelas.
 */
public class JdbcRelatorioDao {

    public Connection abrirConexao() throws SQLException {
        String host = getenvOrDefault("POSTGRES_HOST", "localhost");
        String port = getenvOrDefault("POSTGRES_PORT", "5432");
        String database = getenvOrDefault("POSTGRES_DB", "edufeedback");
        String user = getenvOrDefault("POSTGRES_USER", "postgres");
        String password = getenvOrDefault("POSTGRES_PASSWORD", "postgres");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
        return DriverManager.getConnection(url, user, password);
    }

    public Agregados calcularAgregados(Connection conn) throws SQLException {
        double mediaNota = 0;
        long total = 0;
        Map<String, Long> porDia = new LinkedHashMap<>();
        Map<String, Long> porUrgencia = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT AVG(nota)::numeric(4,2) AS media, COUNT(*) AS total FROM avaliacoes");
                ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                mediaNota = rs.getDouble("media");
                total = rs.getLong("total");
            }
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT to_char(criado_em, 'YYYY-MM-DD') AS dia, COUNT(*) AS quantidade "
                        + "FROM avaliacoes GROUP BY dia ORDER BY dia");
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                porDia.put(rs.getString("dia"), rs.getLong("quantidade"));
            }
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT urgencia, COUNT(*) AS quantidade FROM avaliacoes GROUP BY urgencia");
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                porUrgencia.put(rs.getString("urgencia"), rs.getLong("quantidade"));
            }
        }

        List<AvaliacaoResumo> avaliacoes = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT descricao, urgencia, criado_em FROM avaliacoes ORDER BY criado_em");
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                avaliacoes.add(new AvaliacaoResumo(
                        rs.getString("descricao"),
                        rs.getString("urgencia"),
                        rs.getTimestamp("criado_em").toInstant()));
            }
        }

        return new Agregados(mediaNota, total, porDia, porUrgencia, avaliacoes, Instant.now());
    }

    public void inserirRelatorioAgendado(Connection conn, UUID id, String conteudoJson) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO relatorios (id, tipo, status, solicitado_em, concluido_em, conteudo) "
                        + "VALUES (?, 'AGENDADO', 'CONCLUIDO', ?, ?, ?::jsonb)")) {
            Timestamp agora = Timestamp.from(Instant.now());
            stmt.setObject(1, id);
            stmt.setTimestamp(2, agora);
            stmt.setTimestamp(3, agora);
            stmt.setString(4, conteudoJson);
            stmt.executeUpdate();
        }
    }

    public void concluirRelatorio(Connection conn, UUID id, String conteudoJson) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE relatorios SET status = 'CONCLUIDO', concluido_em = ?, conteudo = ?::jsonb WHERE id = ?")) {
            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setString(2, conteudoJson);
            stmt.setObject(3, id);
            stmt.executeUpdate();
        }
    }

    public void marcarComoErro(Connection conn, UUID id, String mensagemErro) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE relatorios SET status = 'ERRO', concluido_em = ?, conteudo = ?::jsonb WHERE id = ?")) {
            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setString(2, String.format("{\"erro\":\"%s\"}", mensagemErro.replace("\"", "'")));
            stmt.setObject(3, id);
            stmt.executeUpdate();
        }
    }

    public List<String> buscarEmailsAdmins(Connection conn) throws SQLException {
        List<String> emails = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT email FROM admins");
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                emails.add(rs.getString("email"));
            }
        }
        return emails;
    }

    private static String getenvOrDefault(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }
}
