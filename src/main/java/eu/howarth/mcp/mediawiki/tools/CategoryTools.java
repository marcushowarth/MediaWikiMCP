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
public class CategoryTools {

    @Inject
    MediaWikiClient wiki;

    @Tool(description = "List all pages in a MediaWiki category")
    public String listCategory(
            @ToolArg(description = "Category name (without 'Category:' prefix)") String category,
            @ToolArg(description = "Maximum number of results (default 50)") String limit) {
        String effectiveLimit = (limit == null || limit.isBlank()) ? "50" : limit;
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "list", "categorymembers",
                "cmtitle", "Category:" + category,
                "cmlimit", effectiveLimit,
                "cmprop", "title|type",
                "format", "json"
        ));
        JsonNode members = resp.at("/query/categorymembers");
        if (members.isEmpty()) {
            return "No pages in category: " + category;
        }
        List<String> lines = new ArrayList<>();
        members.forEach(m -> lines.add("- [[" + m.at("/title").asText() + "]]"));
        return String.join("\n", lines);
    }

    @Tool(description = "List all categories on a MediaWiki page")
    public String getPageCategories(@ToolArg(description = "Page title") String title) {
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "prop", "categories",
                "titles", title,
                "cllimit", "50",
                "format", "json"
        ));
        JsonNode pages = resp.at("/query/pages");
        JsonNode page = pages.elements().next();
        if (page.has("missing")) {
            return "Page not found: " + title;
        }
        JsonNode cats = page.at("/categories");
        if (cats.isMissingNode() || cats.isEmpty()) {
            return "No categories on: " + title;
        }
        List<String> lines = new ArrayList<>();
        cats.forEach(c -> lines.add("- " + c.at("/title").asText().replace("Category:", "")));
        return String.join("\n", lines);
    }
}
