package eu.howarth.mcp.mediawiki;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The OAuth-secured MCP entry point (Claude Connectors — Desktop/web/mobile),
 * separate from the existing bearer-token path used by Claude Code
 * ({@code /mediawiki/mcp}, gated at Caddy, unaffected by this).
 *
 * Direct port of KanbanMCP's OAuthMcpEndpointTest — same recipe, proven in
 * production for #966 (KanbanMCP half done 2026-08-25). See that file's
 * comments for the incident history behind each assertion.
 */
@QuarkusTest
class OAuthMcpEndpointTest {

    @Inject
    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    @BeforeEach
    void resetBasePath() {
        // @QuarkusTest defaults RestAssured.basePath to quarkus.http.root-path (/mediawiki);
        // reset it so the literal path below isn't doubled up.
        RestAssured.basePath = "";
    }

    @Test
    void unauthenticatedRequestGetsResourceMetadataChallenge() {
        given()
                .when().post("/mediawiki/oauth/mcp")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", containsString("resource_metadata"));
    }

    @Inject
    @ConfigProperty(name = "quarkus.oidc.resource-metadata.resource")
    String configuredResourceUrl;

    @Test
    void protectedResourceMetadataResourceIsConfiguredToMatchTheConnectorUrlExactly() {
        // Anthropic requires the RFC 9728 "resource" field to match the Connector URL
        // exactly, including path — Quarkus defaults this to the bare origin otherwise.
        assertEquals("https://mcp.howarth.eu/mediawiki/oauth/mcp", configuredResourceUrl);
    }

    @Inject
    @ConfigProperty(name = "quarkus.oidc.resource-metadata.scopes")
    java.util.List<String> configuredScopes;

    @Test
    void protectedResourceMetadataAdvertisesTheScopesTheClientActuallyHas() {
        // Left unset, a real Claude Connector attempt fails at the authorization step
        // with oauth_error=invalid_scope (KanbanMCP #966, reproduced live 2026-08-25).
        assertEquals(java.util.List.of("openid", "offline_access"), configuredScopes);
    }

    @Test
    void bearerPathIsNotInterceptedByOidc() {
        // Regression test for the KanbanMCP production incident (2026-08-25): proactive
        // OIDC auth intercepting the pre-existing bearer-token path app-wide. The app
        // itself must impose no OIDC auth on /mediawiki/mcp — Caddy is what gates it.
        given()
                .header("Authorization", "Bearer some-static-non-jwt-token")
                .when().post("/mediawiki/mcp")
                .then()
                .header("WWW-Authenticate", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void validTokenReachesTheEndpoint() {
        String accessToken = given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "password")
                .formParam("client_id", "mediawiki-mcp-test-client")
                .formParam("username", "test-user")
                .formParam("password", "test-password")
                .when().post(authServerUrl + "/protocol/openid-connect/token")
                .then().statusCode(200)
                .extract().path("access_token");

        given()
                .auth().oauth2(accessToken)
                .when().post("/mediawiki/oauth/mcp")
                .then()
                // Not asserting 200 — an MCP streamable-HTTP POST needs a real JSON-RPC
                // body. The point is that a valid token clears authentication: proven by
                // NOT getting 401 again.
                .statusCode(org.hamcrest.Matchers.not(401));
    }
}
