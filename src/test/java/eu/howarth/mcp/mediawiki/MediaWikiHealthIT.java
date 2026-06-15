package eu.howarth.mcp.mediawiki;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Native smoke test: runs against the compiled native binary (a direct process,
 * not a Docker -p container, so it sidesteps the OrbStack localhost-hang trap).
 * Proves the native image boots and serves health. Only runs under the 'native'
 * profile (failsafe / -Dnative). Health path is /health (smallrye-health
 * root-path is absolute, independent of the /mediawiki http root-path).
 */
@QuarkusIntegrationTest
class MediaWikiHealthIT {

    @Test
    void healthIsUp() {
        RestAssured.basePath = "";
        given()
                .when().get("/health")
                .then().statusCode(200)
                .body("status", is("UP"));
    }
}
