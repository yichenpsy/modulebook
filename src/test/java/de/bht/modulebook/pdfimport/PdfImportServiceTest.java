package de.bht.modulebook.pdfimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.config.PdfImportProperties;
import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesRequest;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesResponse;
import de.bht.modulebook.studyprogram.StudyProgram;
import de.bht.modulebook.studyprogram.StudyProgramService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PdfImportServiceTest {

    // Used only to create JsonNode examples for SaveImportedModulesRequest.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    // These dependencies are required by PdfImportService, but not used by saveImportedModules().
    @Mock
    private PythonPdfImportRunner runner;

    @Mock
    private PdfImportProperties properties;

    // Mock the module layer so this unit test does not write to the database.
    @Mock
    private ModuleService moduleService;

    // Mock the mapper so this test focuses on service workflow, not field mapping.
    @Mock
    private PdfImportedModuleMapper importedModuleMapper;

    @Mock
    private StudyProgramService studyProgramService;

    private PdfImportService service;

    @BeforeEach
    void setUp() {
        // Build the service manually so the test stays small and does not start Spring.
        service = new PdfImportService(
                runner,
                properties,
                objectMapper,
                moduleService,
                importedModuleMapper,
                studyProgramService
        );
    }

    @Test
    void savesValidImportedModule() throws Exception {
        // Arrange: one reviewed imported module from the frontend.
        JsonNode imported = objectMapper.readTree("""
                {
                  "module_number": "B01",
                  "title": "Test Module",
                  "credits": 5.0
                }
                """);

        // The mapper converts imported JSON into the existing Module model.
        Module mapped = new Module();
        mapped.setCode("B01");
        mapped.setName("Test Module");
        mapped.setCredits(new BigDecimal("5.0"));
        mapped.setDescription("description");

        // ModuleService returns the saved row, including database id and timestamps.
        Module created = new Module(
                1L,
                10L,
                "B01",
                "Test Module",
                new BigDecimal("5.0"),
                "description",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        // Define how the mocked collaborators should behave for this example.
        when(importedModuleMapper.moduleNumber(imported)).thenReturn("B01");
        when(studyProgramService.findOrCreate("Architektur", 2021)).thenReturn(new StudyProgram(
                10L,
                "Architektur",
                2021,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        ));
        when(importedModuleMapper.toModule(imported, "Architektur", 2021)).thenReturn(mapped);
        when(moduleService.create(mapped)).thenReturn(created);

        // This is the request body shape sent to POST /api/pdf-import/modules.
        SaveImportedModulesRequest request = new SaveImportedModulesRequest(
                "Architektur",
                2021,
                List.of(imported)
        );

        // Act: run the save workflow.
        SaveImportedModulesResponse response = service.saveImportedModules(request);

        // Assert: the service reports one successful creation and no row errors.
        assertThat(response.requestedCount()).isEqualTo(1);
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.errorCount()).isZero();
        assertThat(response.createdModules()).hasSize(1);
        assertThat(response.createdModules().get(0).id()).isEqualTo(1L);
        assertThat(response.createdModules().get(0).studyProgramId()).isEqualTo(10L);
        assertThat(response.createdModules().get(0).code()).isEqualTo("B01");
        assertThat(response.errors()).isEmpty();
        verify(moduleService).create(mapped);
        assertThat(mapped.getStudyProgramId()).isEqualTo(10L);
    }

    @Test
    void returnsErrorWhenImportedModuleCannotBeSaved() throws Exception {
        // Arrange: one reviewed imported module that will fail during saving.
        JsonNode imported = objectMapper.readTree("""
                {
                  "module_number": "B01",
                  "title": "Test Module",
                  "credits": 5.0
                }
                """);

        Module mapped = new Module();
        mapped.setCode("B01");

        // Simulate a save failure, such as a duplicate module code from the database.
        when(importedModuleMapper.moduleNumber(imported)).thenReturn("B01");
        when(studyProgramService.findOrCreate("Architektur", 2021)).thenReturn(new StudyProgram(
                10L,
                "Architektur",
                2021,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        ));
        when(importedModuleMapper.toModule(imported, "Architektur", 2021)).thenReturn(mapped);
        when(moduleService.create(mapped)).thenThrow(new IllegalArgumentException("Duplicate module code"));

        SaveImportedModulesRequest request = new SaveImportedModulesRequest(
                "Architektur",
                2021,
                List.of(imported)
        );

        // Act: the service should catch the row failure instead of throwing it.
        SaveImportedModulesResponse response = service.saveImportedModules(request);

        // Assert: no module was created, and the failed row appears in errors.
        assertThat(response.requestedCount()).isEqualTo(1);
        assertThat(response.createdCount()).isZero();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.createdModules()).isEmpty();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).index()).isZero();
        assertThat(response.errors().get(0).moduleNumber()).isEqualTo("B01");
        assertThat(response.errors().get(0).message()).contains("Duplicate module code");
    }

    @Test
    void rejectsEmptyModuleList() {
        // Arrange: saving nothing is a bad request because there is no work to perform.
        SaveImportedModulesRequest request = new SaveImportedModulesRequest(
                "Architektur",
                2021,
                List.of()
        );

        // Assert: the service rejects the request before trying to call mapper or database code.
        assertThatThrownBy(() -> service.saveImportedModules(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException = (ResponseStatusException) exception;
                    assertThat(responseException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .hasMessageContaining("No modules provided");
    }
}
