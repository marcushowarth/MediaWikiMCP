package eu.howarth.mcp.mediawiki;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import eu.howarth.mcp.mediawiki.client.WikiClient;
import eu.howarth.mcp.mediawiki.tools.PageTools;
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
class EditSectionTest {

    @InjectMock
    WikiClient wiki;

    @Inject
    PageTools pageTools;

    @Test
    void editSectionPassesOnlyProvidedContent() {
        var fakeResponse = JsonNodeFactory.instance.objectNode()
                .put("result", "Success");
        var editNode = JsonNodeFactory.instance.objectNode();
        editNode.put("newrevid", 12345);
        var root = JsonNodeFactory.instance.objectNode();
        root.set("edit", editNode);
        when(wiki.edit(any())).thenReturn(root);

        pageTools.editSection("Test Page", "2", "== My Section ==\nNew content only.", "test edit");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(wiki).edit(captor.capture());

        Map<String, String> params = captor.getValue();
        assertEquals("2", params.get("section"));
        assertEquals("== My Section ==\nNew content only.", params.get("text"));
        assertEquals("Test Page", params.get("title"));
        assertEquals("test edit", params.get("summary"));
    }
}
