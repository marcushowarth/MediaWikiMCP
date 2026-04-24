package eu.howarth.mcp.mediawiki.tools;

import com.fasterxml.jackson.databind.JsonNode;
import eu.howarth.mcp.mediawiki.client.MediaWikiClient;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SearchTools {

    @Inject
    MediaWikiClient wiki;

    @Tool(description = "Search MediaWiki pages by keyword. Returns page titles and snippets.")
    public String search(
            @ToolArg(description = "Search query") String query,
            @ToolArg(description = "Maximum number of results (default 10, max 50)") String limit) {
        String effectiveLimit = (limit == null || limit.isBlank()) ? "10" : limit;
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "list", "search",
                "srsearch", query,
                "srlimit", effectiveLimit,
                "srprop", "snippet|titlesnippet",
                "format", "json"
        ));
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
            @ToolArg(description = "Maximum number of results (default 20)") String limit) {
        String effectiveLimit = (limit == null || limit.isBlank()) ? "20" : limit;
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "list", "backlinks",
                "bltitle", title,
                "bllimit", effectiveLimit,
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
}
