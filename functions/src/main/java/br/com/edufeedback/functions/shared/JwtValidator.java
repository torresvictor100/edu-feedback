package br.com.edufeedback.functions.shared;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Valida o mesmo JWT emitido pelo Serviço A (Spring Boot) — HS256 com segredo
 * compartilhado via variável de ambiente JWT_SECRET. Usado apenas pela função
 * HTTP (solicitação de relatório sob demanda), que precisa restringir o acesso a ADMIN.
 */
@ApplicationScoped
public class JwtValidator {

    private final SecretKey signingKey;

    public JwtValidator(@ConfigProperty(name = "jwt.secret") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean tokenValidoParaAdmin(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authorizationHeader.substring("Bearer ".length());
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return "ADMIN".equals(claims.get("role", String.class));
        } catch (Exception e) {
            return false;
        }
    }
}
