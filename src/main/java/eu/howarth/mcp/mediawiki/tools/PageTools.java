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

    @Tool(description = "Create or overwrite a MediaWiki page. Provide content directly, or use templateTitle to preload an existing page as the base. If both are given, template content is prepended before content.")
    public String createPage(
            @ToolArg(description = "Page title") String title,
            @ToolArg(description = "Wikitext content for the page. Required if templateTitle is not given.") Optional<String> content,
            @ToolArg(description = "Edit summary") String summary,
            @ToolArg(description = "Title of an existing page to use as preload template. Its wikitext becomes the base content. Use prefixSearch with 'Template:' to discover available templates. Omit if providing content directly.") Optional<String> templateTitle) {
        String rawContent = content.filter(s -> !s.isBlank()).orElse(null);
        String rawTemplate = templateTitle.filter(s -> !s.isBlank()).orElse(null);
        String pageContent;
        if (rawTemplate != null) {
            String templateContent = fetchPageContent(rawTemplate);
            if (templateContent.startsWith("Error:") || templateContent.startsWith("Page not found:")) {
                return "Cannot load template '" + rawTemplate + "': " + templateContent;
            }
            pageContent = rawContent != null ? templateContent + "\n" + rawContent : templateContent;
        } else if (rawContent != null) {
            pageContent = rawContent;
        } else {
            return "Error: provide either content or templateTitle";
        }
        JsonNode resp = wiki.edit(Map.of(
                "action", "edit",
                "title", title,
                "text", pageContent,
                "summary", summary
        ));
        if (resp.has("error")) {
            return "Error: " + resp.at("/error/info").asText();
        }
        return "Created: " + title + " (revid " + resp.at("/edit/newrevid").asText() + ")";
    }

    private String fetchPageContent(String title) {
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

    @Tool(description = "Get the wikitext of a single section by index. Use getSections to find index numbers. Cheaper than getPage on large pages.")
    public String getSection(
            @ToolArg(description = "Page title") String title,
            @ToolArg(description = "Section number (0 for lead section)") String section) {
        JsonNode resp = wiki.get(Map.of(
                "action", "parse",
                "page", title,
                "section", section,
                "prop", "wikitext",
                "format", "json"
        ));
        if (resp.has("error")) {
            return "Error: " + resp.at("/error/info").asText();
        }
        return resp.at("/parse/wikitext/*").asText();
    }

    @Tool(description = "Append content to a section without overwriting existing content. Safe for journal-style additions. Reads the section first then writes back the combined content.")
    public String appendToSection(
            @ToolArg(description = "Page title") String title,
            @ToolArg(description = "Section number (0 for lead section)") String section,
            @ToolArg(description = "Wikitext to append to the section") String content,
            @ToolArg(description = "Edit summary") String summary) {
        JsonNode readResp = wiki.get(Map.of(
                "action", "parse",
                "page", title,
                "section", section,
                "prop", "wikitext",
                "format", "json"
        ));
        if (readResp.has("error")) {
            return "Error reading section: " + readResp.at("/error/info").asText();
        }
        String existing = readResp.at("/parse/wikitext/*").asText();
        JsonNode writeResp = wiki.edit(Map.of(
                "action", "edit",
                "title", title,
                "section", section,
                "text", existing + "\n" + content,
                "summary", summary
        ));
        if (writeResp.has("error")) {
            return "Error writing section: " + writeResp.at("/error/info").asText();
        }
        return "Appended to section " + section + " of: " + title + " (revid " + writeResp.at("/edit/newrevid").asText() + ")";
    }

    @Tool(description = "Get revision history of a MediaWiki page. Returns recent edits with revision IDs, timestamps, editors and summaries.")
    public String getPageHistory(
            @ToolArg(description = "Page title") String title,
            @ToolArg(description = "Number of revisions to return (1–50)") int limit) {
        JsonNode resp = wiki.get(Map.of(
                "action", "query",
                "prop", "revisions",
                "titles", title,
                "rvprop", "ids|timestamp|user|comment|size",
                "rvlimit", String.valueOf(limit),
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
        List<String> lines = new ArrayList<>();
        lines.add("History of [[" + title + "]]:");
        page.at("/revisions").forEach(r -> lines.add(
                r.at("/timestamp").asText()
                + " | revid=" + r.at("/revid").asText()
                + " | " + r.at("/user").asText()
                + " | " + r.at("/comment").asText()
                + " | size=" + r.at("/size").asText()
        ));
        return String.join("\n", lines);
    }

    @Tool(description = "Overwrite a section of a MediaWiki page by section number (0 = lead section). A section includes any indented child sections. WARNING: this replaces the entire section content. Always read the section first with getPage or getSections before calling this.")
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
