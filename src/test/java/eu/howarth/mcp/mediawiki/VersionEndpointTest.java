package eu.howarth.mcp.mediawiki;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Build provenance (version-build-provenance pattern, #1224): which build is
 * live, readable without a token so the ops hub's "Running build" panel can
 * show it. It holds nothing secret. Caddy lets this one path past the token.
 */
@QuarkusTest
class VersionEndpointTest {

    @BeforeEach
    void resetBasePath() {
        RestAssured.basePath = "";
    }

    @Test
    void theVersionAnswersWithoutAnyToken_withTheFourProvenanceFields() {
        given()
                .when().get("/mediawiki/version")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("version", not(emptyString()))
                .body("buildNumber", notNullValue())
                .body("gitSha", notNullValue())
                .body("builtAt", notNullValue());
    }

    @Test
    void withNoBuildInformationSet_itSaysSoInsteadOfFailing() {
        given()
                .when().get("/mediawiki/version")
                .then()
                .body("buildNumber", is("dev"))
                .body("gitSha", is("unknown"))
                .body("builtAt", is("unknown"));
    }
}
