package eu.howarth.mcp.mediawiki.tools;

import com.fasterxml.jackson.databind.JsonNode;
import eu.howarth.mcp.mediawiki.client.WikiClient;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class SearchTools {

    @Inject
    WikiClient wiki;

    @Tool(description = "Search MediaWiki pages by keyword. Returns page titles and snippets.")
    public String search(
            @ToolArg(description = "Search query") String query,
            @ToolArg(description = "Maximum number of results (max 50)") int limit,
            @ToolArg(description = "Namespace name to restrict search (e.g. 'Journal', 'Template'). Use listNamespaces to see available namespaces. Omit to search all.") String namespace) {
        var params = new java.util.HashMap<>(Map.of(
                "action", "query",
                "list", "search",
                "srsearch", query,
                "srlimit", String.valueOf(limit <= 0 ? 10 : limit),
                "srprop", "snippet|titlesnippet",
                "format", "json"
        ));
        if (namespace != null && !namespace.isBlank()) {
            resolveNamespaceId(namespace).ifPresent(id -> params.put("srnamespace", id));
        }
        JsonNode resp = wiki.get(params);
        JsonNode results = resp.at("/query/search");
        if (results.isEmpty()) {
            return "No results for: " + query;
        }
        List<String> lines = new ArrayList<>();
        results.forEach(r -> lines.add(
                "- [[" + r.at("/title").asText() + "]] — " +
                r.at("/snippet").asText().replaceAll("<[^>]+>", "")
        ));
        return String.join("\n", lines);
    }

    @Tool(description = "List pages that link to a given MediaWiki page (backlinks)")
    public String getBacklinks(
            @ToolArg(description = "Page title to find backlinks for") String title,
            @ToolArg(description = "Maximum number of results") int limit) {
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "list", "backlinks",
                "bltitle", title,
                "bllimit", String.valueOf(limit <= 0 ? 20 : limit),
                "format", "json"
        ));
        JsonNode backlinks = resp.at("/query/backlinks");
        if (backlinks.isEmpty()) {
            return "No backlinks to: " + title;
        }
        List<String> lines = new ArrayList<>();
        backlinks.forEach(b -> lines.add("- [[" + b.at("/title").asText() + "]]"));
        return String.join("\n", lines);
    }

    @Tool(description = "List MediaWiki pages whose titles start with a given prefix. Use to discover pages in a namespace, e.g. prefix 'Journal:2026' lists all journal entries for 2026.")
    public String prefixSearch(
            @ToolArg(description = "Title prefix to search for (e.g. 'Journal:', 'Journal:2026-04', 'Projects:')") String prefix,
            @ToolArg(description = "Maximum number of results") int limit) {
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "list", "prefixsearch",
                "pssearch", prefix,
                "pslimit", String.valueOf(limit <= 0 ? 20 : limit),
                "format", "json"
        ));
        JsonNode results = resp.at("/query/prefixsearch");
        if (results.isEmpty()) {
            return "No pages found with prefix: " + prefix;
        }
        List<String> lines = new ArrayList<>();
        results.forEach(r -> lines.add("- [[" + r.at("/title").asText() + "]]"));
        return String.join("\n", lines);
    }

    @Tool(description = "List all namespaces on this wiki with their numeric IDs. Use the namespace name with the search tool to restrict results.")
    public String listNamespaces() {
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "meta", "siteinfo",
                "siprop", "namespaces",
                "format", "json"
        ));
        JsonNode namespaces = resp.at("/query/namespaces");
        if (namespaces.isEmpty()) {
            return "Could not retrieve namespaces.";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Namespaces (ID: Name):");
        namespaces.fields().forEachRemaining(e -> {
            int id = Integer.parseInt(e.getKey());
            if (id < 0) return;
            String name = e.getValue().has("*") ? e.getValue().at("/*").asText() : "(main)";
            if (name.isBlank()) name = "(main)";
            lines.add("  " + id + ": " + name);
        });
        return String.join("\n", lines);
    }

    private Optional<String> resolveNamespaceId(String namespaceName) {
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "meta", "siteinfo",
                "siprop", "namespaces",
                "format", "json"
        ));
        JsonNode namespaces = resp.at("/query/namespaces");
        var ref = new Object() { String found = null; };
        namespaces.fields().forEachRemaining(e -> {
            String name = e.getValue().has("*") ? e.getValue().at("/*").asText() : "";
            if (namespaceName.equalsIgnoreCase(name)) {
                ref.found = e.getKey();
            }
        });
        return Optional.ofNullable(ref.found);
    }

    @Tool(description = "List recent changes to the wiki. Returns page titles, editor, timestamp and edit summary.")
    public String listRecentChanges(
            @ToolArg(description = "Maximum number of results") int limit) {
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "list", "recentchanges",
                "rcprop", "title|timestamp|user|comment|sizes",
                "rclimit", String.valueOf(limit <= 0 ? 20 : limit),
                "rctype", "edit|new",
                "format", "json"
        ));
        JsonNode changes = resp.at("/query/recentchanges");
        if (changes.isEmpty()) {
            return "No recent changes found.";
        }
        List<String> lines = new ArrayList<>();
        changes.forEach(c -> {
            String timestamp = c.at("/timestamp").asText().replace("T", " ").replace("Z", "");
            String comment = c.at("/comment").asText();
            String summary = comment.isBlank() ? "" : " — " + comment;
            lines.add(timestamp + "  [[" + c.at("/title").asText() + "]] by " + c.at("/user").asText() + summary);
        });
        return String.join("\n", lines);
    }
}
