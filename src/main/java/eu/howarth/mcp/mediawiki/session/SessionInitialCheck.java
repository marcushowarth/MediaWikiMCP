package eu.howarth.mcp.mediawiki.session;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SessionInitialCheck implements InitialCheck {

    private static final Logger LOG = Logger.getLogger(SessionInitialCheck.class);

    @Override
    public Uni<CheckResult> perform(InitialRequest request) {
        String clientName = request.implementation() != null ? request.implementation().name() : "unknown";
        String protocolVersion = request.protocolVersion();

        LOG.infof("MCP client connecting: name=%s, protocol=%s, transport=%s",
                clientName, protocolVersion, request.transport());

        if (protocolVersion == null || protocolVersion.isBlank()) {
            return CheckResult.error(
                    "Missing protocolVersion in initialize request — client must send a valid MCP protocol version string (e.g. '2024-11-05')");
        }

        if (request.autoInitialized()) {
            LOG.warnf("Client '%s' was auto-initialized — notifications/initialized was not sent; "
                    + "ensure the client completes the handshake: initialize → notifications/initialized → tool calls",
                    clientName);
        }

        LOG.infof("MCP session accepted: client=%s, protocol=%s", clientName, protocolVersion);
        return CheckResult.success();
    }
}