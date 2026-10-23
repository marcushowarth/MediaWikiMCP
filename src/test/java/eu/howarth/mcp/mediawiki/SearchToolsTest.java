package eu.howarth.mcp.mediawiki;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import eu.howarth.mcp.mediawiki.client.WikiClient;
import eu.howarth.mcp.mediawiki.tools.SearchTools;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SearchToolsTest {

    @InjectMock
    WikiClient wiki;

    @Inject
    SearchTools searchTools;

    /**
     * MediaWiki 1.46 with the default MySQL search backend returns only title
     * matches unless the request asks for a text search, so srwhat must be sent.
     */
    @Test
    void searchAsksForAFullTextSearch() {
        var root = JsonNodeFactory.instance.objectNode();
        root.putObject("query").putArray("search");
        when(wiki.get(any())).thenReturn(root);

        searchTools.search("staging", 5, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(wiki).get(captor.capture());

        Map<String, String> params = captor.getValue();
        assertEquals("search", params.get("list"));
        assertEquals("staging", params.get("srsearch"));
        assertEquals("text", params.get("srwhat"));
    }
}
