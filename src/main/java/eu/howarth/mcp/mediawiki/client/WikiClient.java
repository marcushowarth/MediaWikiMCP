package eu.howarth.mcp.mediawiki.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface WikiClient {
    JsonNode get(Map<String, String> params);
    JsonNode edit(Map<String, String> params);
}
