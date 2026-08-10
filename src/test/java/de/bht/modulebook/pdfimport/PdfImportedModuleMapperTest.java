package de.bht.modulebook.pdfimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.bht.modulebook.module.Module;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PdfImportedModuleMapperTest {

    // Use the same Jackson API as the application so test JSON behaves like request JSON.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    // This is a pure mapper, so it can be tested directly without Spring.
    private final PdfImportedModuleMapper mapper = new PdfImportedModuleMapper();

    @Test
    void mapsImportedJsonToModule() throws Exception {
        // Arrange: build one example of the normalized JSON shape produced by the PDF import.
        JsonNode imported = objectMapper.readTree("""
                {
                  "module_number": "B01",
                  "title": "Test Module",
                  "credits": 5.0,
                  "contents": "Test contents",
                  "learning_objectives": "Test objectives",
                  "source_file": "test.pdf",
                  "source_pages": [1, 2]
                }
                """);

        // Act: convert the imported JSON into the existing Module model.
        Module module = mapper.toModule(imported, "Architektur", 2021);

        // Assert: required fields are mapped to the database-facing Module fields.
        assertThat(module.getCode()).isEqualTo("B01");
        assertThat(module.getName()).isEqualTo("Test Module");
        assertThat(module.getCredits()).isEqualByComparingTo("5.0");

        // Assert: PDF-specific details are preserved inside the current description field.
        assertThat(module.getDescription()).contains("Architektur");
        assertThat(module.getDescription()).contains("Test contents");
        assertThat(module.getDescription()).contains("Test objectives");
        assertThat(module.getDescription()).contains("test.pdf");
    }

    @Test
    void rejectsMissingModuleNumber() throws Exception {
        // Arrange: title and credits are present, but the required module_number is missing.
        JsonNode imported = objectMapper.readTree("""
                {
                  "title": "Test Module",
                  "credits": 5.0
                }
                """);

        // Assert: invalid import data should fail with a clear validation message.
        assertThatThrownBy(() -> mapper.toModule(imported, "Architektur", 2021))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing module_number");
    }
}
