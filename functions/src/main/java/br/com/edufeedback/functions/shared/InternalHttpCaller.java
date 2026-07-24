package br.com.edufeedback.functions.shared;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

/**
 * Chama os endpoints internos do Quarkus (ver ADR-006 em docs/DECISIONS.md) a
 * partir dos gatilhos nativos (Timer/Queue). Usada só por classes que não têm
 * CDI — por isso é uma classe simples, sem anotações de framework.
 *
 * Em produção, a Azure injeta automaticamente a variável WEBSITE_HOSTNAME com o
 * hostname público do próprio Function App. Localmente (Azure Functions Core
 * Tools), essa variável não existe — o padrão cai para localhost:7071.
 */
public class InternalHttpCaller {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public HttpResponse<String> post(String caminho, String corpoJson) throws Exception {
        String baseUrl = baseUrl();
        String segredo = getenvOrDefault("INTERNAL_TRIGGER_SECRET", "changeit-local-dev-internal-secret");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + caminho))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Internal-Secret", segredo)
                .POST(corpoJson == null
                        ? BodyPublishers.noBody()
                        : BodyPublishers.ofString(corpoJson))
                .build();

        return CLIENT.send(request, BodyHandlers.ofString());
    }

    private String baseUrl() {
        String hostname = System.getenv("WEBSITE_HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            return "http://localhost:7071/api";
        }
        return "https://" + hostname + "/api";
    }

    private static String getenvOrDefault(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }
}
