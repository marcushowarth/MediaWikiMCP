package eu.howarth.mcp.mediawiki.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.howarth.mcp.mediawiki.config.MediaWikiProperties;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Startup
@ApplicationScoped
public class MediaWikiClient {

    @Inject
    MediaWikiProperties config;

    private HttpClient http;
    private ObjectMapper mapper;
    private volatile String csrfToken;

    @PostConstruct
    void init() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        http = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        mapper = new ObjectMapper();
        login();
    }

    private void login() {
        try {
            JsonNode tokenResp = get(Map.of("action", "query", "meta", "tokens", "type", "login", "format", "json"));
            String loginToken = tokenResp.at("/query/tokens/logintoken").asText();

            JsonNode loginResp = post(Map.of(
                    "action", "login",
                    "lgname", config.botUser(),
                    "lgpassword", config.botPassword(),
                    "lgtoken", loginToken,
                    "format", "json"
            ));

            String result = loginResp.at("/login/result").asText();
            if (!"Success".equals(result)) {
                throw new MediaWikiException("Login failed: " + result + " — " + loginResp.at("/login/reason").asText());
            }

            refreshCsrfToken();
        } catch (MediaWikiException e) {
            throw e;
        } catch (Exception e) {
            throw new MediaWikiException("Login error", e);
        }
    }

    private void refreshCsrfToken() {
        JsonNode resp = get(Map.of("action", "query", "meta", "tokens", "type", "csrf", "format", "json"));
        csrfToken = resp.at("/query/tokens/csrftoken").asText();
    }

    public JsonNode get(Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.url() + "?" + query))
                    .GET()
                    .build();
            String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
            return mapper.readTree(body);
        } catch (IOException | InterruptedException e) {
            throw new MediaWikiException("GET failed", e);
        }
    }

    public JsonNode post(Map<String, String> params) {
        String form = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.url()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
            return mapper.readTree(body);
        } catch (IOException | InterruptedException e) {
            throw new MediaWikiException("POST failed", e);
        }
    }

    public JsonNode edit(Map<String, String> params) {
        var allParams = new java.util.HashMap<>(params);
        allParams.put("token", csrfToken);
        allParams.put("format", "json");

        JsonNode resp = post(allParams);

        if (resp.has("error")) {
            String code = resp.at("/error/code").asText();
            if ("badtoken".equals(code) || "notoken".equals(code)) {
                refreshCsrfToken();
                allParams.put("token", csrfToken);
                resp = post(allParams);
            }
        }
        return resp;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
