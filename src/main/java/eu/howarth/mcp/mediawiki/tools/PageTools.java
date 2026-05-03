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

@ApplicationScoped
public class PageTools {

    @Inject
    WikiClient wiki;

    @Tool(description = "List all sections of a MediaWiki page with their index numbers, levels and titles. Use the index with editSection.")
    public String getSections(@ToolArg(description = "Page title") String title) {
        JsonNode resp = wiki.get(Map.of(
                "action", "parse",
                "page", title,
                "prop", "sections",
                "format", "json"
        ));
        if (resp.has("error")) {
            return "Error: " + resp.at("/error/info").asText();
        }
        JsonNode sections = resp.at("/parse/sections");
        if (sections.isEmpty()) {
            return "No sections found in: " + title;
        }
        List<String> lines = new ArrayList<>();
        lines.add("Sections of [[" + title + "]]:");
        sections.forEach(s -> {
            String indent = "  ".repeat(s.at("/toclevel").asInt(1) - 1);
            lines.add(indent + "[" + s.at("/index").asText() + "] "
                    + "H" + s.at("/level").asText() + " — "
                    + s.at("/line").asText());
        });
        return String.join("\n", lines);
    }

    @Tool(description = "Get the wikitext content of a MediaWiki page by title")
    public String getPage(@ToolArg(description = "Page title") String title) {
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "prop", "revisions",
                "rvprop", "content",
                "rvslots", "main",
                "titles", title,
                "format", "json"
        ));
        if (resp.has("error")) {
            return "Error: " + resp.at("/error/info").asText();
        }
        JsonNode pages = resp.at("/query/pages");
        if (!pages.elements().hasNext()) {
            return "Page not found: " + title;
        }
        JsonNode page = pages.elements().next();
        if (page.has("missing")) {
            return "Page not found: " + title;
        }
        return page.at("/revisions/0/slots/main/*").asText();
    }

    @Tool(description = "Create or overwrite a MediaWiki page with the given wikitext content")
    public String createPage(
            @ToolArg(description = "Page title") String title,
            @ToolArg(description = "Full wikitext content for the page") String content,
            @ToolArg(description = "Edit summary") String summary) {
        JsonNode resp = wiki.edit(Map.of(
                "action", "edit",
                "title", title,
                "text", content,
                "summary", summary
        ));
        if (resp.has("error")) {
            return "Error: " + resp.at("/error/info").asText();
        }
        return "Created: " + title + " (revid " + resp.at("/edit/newrevid").asText() + ")";
    }

    @Tool(description = "Append text to the end of an existing MediaWiki page")
    public String appendToPage(
            @ToolArg(description = "Page title") String title,
            @ToolArg(description = "Wikitext to append") String text,
            @ToolArg(description = "Edit summary") String summary) {
        JsonNode resp = wiki.edit(Map.of(
                "action", "edit",
                "title", title,
                "appendtext", text,
                "summary", summary
        ));
        if (resp.has("error")) {
            return "Error: " + resp.at("/error/info").asText();
        }
        return "Appended to: " + title + " (revid " + resp.at("/edit/newrevid").asText() + ")";
    }

    @Tool(description = "Overwrite a section of a MediaWiki page by section number (0 = lead section). WARNING: this replaces the entire section content. Always read the section first with getPage or getSections before calling this.")
    public String editSection(
            @ToolArg(description = "Page title") String title,
            @ToolArg(description = "Section number (0 for lead section)") String section,
            @ToolArg(description = "New wikitext content for the section") String content,
            @ToolArg(description = "Edit summary") String summary) {
        JsonNode resp = wiki.edit(Map.of(
                "action", "edit",
                "title", title,
                "section", section,
                "text", content,
                "summary", summary
        ));
        if (resp.has("error")) {
            return "Error: " + resp.at("/error/info").asText();
        }
        return "Edited section " + section + " of: " + title + " (revid " + resp.at("/edit/newrevid").asText() + ")";
    }
}
