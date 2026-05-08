package eu.howarth.mcp.mediawiki.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.howarth.mcp.mediawiki.config.MediaWikiProperties;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

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

@ApplicationScoped
public class MediaWikiClient implements WikiClient {

    private static final Logger LOG = Logger.getLogger(MediaWikiClient.class);

    @Inject
    MediaWikiProperties config;

    private HttpClient http;
    private ObjectMapper mapper;
    private volatile String csrfToken;
    private volatile boolean loggedIn = false;

    @PostConstruct
    void init() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        http = HttpClient.newBuilder().cookieHandler(cookieManager).build();
        mapper = new ObjectMapper();
    }

    private synchronized void ensureLoggedIn() {
        if (!loggedIn) {
            login();
        }
    }

    private void login() {
        JsonNode tokenResp = rawGet(Map.of("action", "query", "meta", "tokens", "type", "login", "format", "json"));
        String loginToken = tokenResp.at("/query/tokens/logintoken").asText();

        JsonNode loginResp = rawPost(Map.of(
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

        JsonNode csrfResp = rawGet(Map.of("action", "query", "meta", "tokens", "type", "csrf", "format", "json"));
        csrfToken = csrfResp.at("/query/tokens/csrftoken").asText();
        loggedIn = true;
        LOG.infof("Logged in to MediaWiki as %s", config.botUser());
    }

    public JsonNode get(Map<String, String> params) {
        ensureLoggedIn();
        JsonNode resp = rawGet(params);
        if (resp.has("error")) {
            String code = resp.at("/error/code").asText();
            if ("readapidenied".equals(code) || "permissiondenied".equals(code)) {
                loggedIn = false;
                ensureLoggedIn();
                resp = rawGet(params);
            }
        }
        return resp;
    }

    public JsonNode edit(Map<String, String> params) {
        ensureLoggedIn();
        var allParams = new java.util.HashMap<>(params);
        allParams.put("token", csrfToken);
        allParams.put("format", "json");

        JsonNode resp = rawPost(allParams);

        if (resp.has("error")) {
            String code = resp.at("/error/code").asText();
            if ("badtoken".equals(code) || "notoken".equals(code)) {
                loggedIn = false;
                ensureLoggedIn();
                allParams.put("token", csrfToken);
                resp = rawPost(allParams);
            }
        }
        return resp;
    }

    private JsonNode rawGet(Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.url() + "?" + query))
                    .GET()
                    .build();
            return mapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        } catch (IOException | InterruptedException e) {
            throw new MediaWikiException("GET failed", e);
        }
    }

    private JsonNode rawPost(Map<String, String> params) {
        String form = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.url()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            return mapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        } catch (IOException | InterruptedException e) {
            throw new MediaWikiException("POST failed", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
