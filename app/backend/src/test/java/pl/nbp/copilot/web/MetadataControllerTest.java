package pl.nbp.copilot.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for GET /api/metadata.
 * ADR-001 §4/§5; ADR-002 §6; TAC-004-06.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getMetadata_returns200() throws Exception {
        mockMvc.perform(get("/api/metadata"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void getMetadata_caseTypes_hasTwoEntries() throws Exception {
        JsonNode root = fetchMetadata();
        JsonNode caseTypes = root.get("caseTypes");

        assertThat(caseTypes).isNotNull();
        assertThat(caseTypes.isArray()).isTrue();
        assertThat(caseTypes.size()).isEqualTo(2);
    }

    @Test
    void getMetadata_caseTypes_hasIdAndLabelPl() throws Exception {
        JsonNode root = fetchMetadata();
        JsonNode caseTypes = root.get("caseTypes");

        for (JsonNode option : caseTypes) {
            assertThat(option.has("id")).isTrue();
            assertThat(option.has("labelPl")).isTrue();
            assertThat(option.get("id").asText()).isNotBlank();
            assertThat(option.get("labelPl").asText()).isNotBlank();
        }
    }

    @Test
    void getMetadata_equipmentCategories_hasThirteenEntries() throws Exception {
        JsonNode root = fetchMetadata();
        JsonNode categories = root.get("equipmentCategories");

        assertThat(categories).isNotNull();
        assertThat(categories.isArray()).isTrue();
        assertThat(categories.size()).isEqualTo(13);
    }

    @Test
    void getMetadata_equipmentCategories_containsOther() throws Exception {
        JsonNode root = fetchMetadata();
        JsonNode categories = root.get("equipmentCategories");

        boolean hasOther = false;
        for (JsonNode option : categories) {
            if ("OTHER".equals(option.get("id").asText())) {
                hasOther = true;
                assertThat(option.get("labelPl").asText()).isNotBlank();
            }
        }
        assertThat(hasOther).as("OTHER category must be present").isTrue();
    }

    @Test
    void getMetadata_equipmentCategories_allHavePolishLabels() throws Exception {
        JsonNode root = fetchMetadata();
        JsonNode categories = root.get("equipmentCategories");

        for (JsonNode option : categories) {
            String id = option.get("id").asText();
            String label = option.get("labelPl").asText();
            assertThat(label)
                    .as("Polish label for category %s must not be blank", id)
                    .isNotBlank();
        }
    }

    @Test
    void getMetadata_imageConstraints_hasAcceptedTypesAndMaxBytes() throws Exception {
        JsonNode root = fetchMetadata();
        JsonNode imageConstraints = root.get("imageConstraints");

        assertThat(imageConstraints).isNotNull();
        assertThat(imageConstraints.has("acceptedTypes")).isTrue();
        assertThat(imageConstraints.has("maxBytes")).isTrue();

        JsonNode acceptedTypes = imageConstraints.get("acceptedTypes");
        assertThat(acceptedTypes.isArray()).isTrue();
        assertThat(acceptedTypes.size()).isGreaterThan(0);

        long maxBytes = imageConstraints.get("maxBytes").asLong();
        assertThat(maxBytes).isGreaterThan(0);
    }

    @Test
    void getMetadata_imageConstraints_reflectsConfig() throws Exception {
        JsonNode root = fetchMetadata();
        JsonNode imageConstraints = root.get("imageConstraints");

        // Default from application.yaml: 10485760 (10 MB)
        long maxBytes = imageConstraints.get("maxBytes").asLong();
        assertThat(maxBytes).isEqualTo(10_485_760L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JsonNode fetchMetadata() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/metadata"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
