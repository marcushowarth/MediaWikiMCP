package eu.howarth.mcp.mediawiki.version;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Which build is live (version-build-provenance pattern, #1224): version from
 * the pom, build number, git sha and build time injected by the deploy as
 * APP_BUILD_NUMBER, APP_GIT_SHA and APP_BUILT_AT. Holds nothing secret, and
 * answers without a token; Caddy lets this one path through.
 */
@ApplicationScoped
public class VersionEndpoint {

    @RegisterForReflection
    public record Version(String version, String buildNumber, String gitSha, String builtAt) {
    }

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @ConfigProperty(name = "app.build-number", defaultValue = "dev")
    String buildNumber;

    @ConfigProperty(name = "app.git-sha", defaultValue = "unknown")
    String gitSha;

    @ConfigProperty(name = "app.built-at", defaultValue = "unknown")
    String builtAt;

    void routes(@Observes Router router) {
        router.get("/version").handler(context -> context.response()
                .putHeader("Content-Type", "application/json")
                .end(json(new Version(version, buildNumber, gitSha, builtAt))));
    }

    // Four short strings: written by hand rather than reflected, so the native
    // image needs nothing registered.
    static String json(Version v) {
        return "{\"version\":\"" + escape(v.version()) + "\",\"buildNumber\":\"" + escape(v.buildNumber())
                + "\",\"gitSha\":\"" + escape(v.gitSha()) + "\",\"builtAt\":\"" + escape(v.builtAt()) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
