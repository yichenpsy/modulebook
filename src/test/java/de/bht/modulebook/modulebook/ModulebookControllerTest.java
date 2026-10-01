package de.bht.modulebook.modulebook;

import static org.assertj.core.api.Assertions.assertThat;
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

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.dto.ModuleResponse;
import de.bht.modulebook.modulebook.dto.CreateModulebookRequest;
import de.bht.modulebook.modulebook.dto.ModulebookResponse;

@ExtendWith(MockitoExtension.class)
class ModulebookControllerTest {

    @Mock
    private ModulebookService modulebookService;

    @Mock
    private ModuleService moduleService;

    private ModulebookController controller;

    @BeforeEach
    void setUp() {
        controller = new ModulebookController(modulebookService, moduleService);
    }

    @Test
    void getAllReturnsModulebookResponses() {
        Modulebook modulebook = new Modulebook(
                10L,
                "Architektur",
                "BACHELOR",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
        when(modulebookService.getAll()).thenReturn(List.of(modulebook));

        List<ModulebookResponse> response = controller.getAll();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(10L);
        assertThat(response.get(0).studyProgramName()).isEqualTo("Architektur");
        assertThat(response.get(0).degreeType()).isEqualTo("BACHELOR");
    }

    @Test
    void createPassesRequestDataToService() {
        ModulebookResponse created = new ModulebookResponse(
                10L,
                "Architektur",
                "BACHELOR",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
        CreateModulebookRequest request = new CreateModulebookRequest("Architektur", "BACHELOR");
        when(modulebookService.create(request)).thenReturn(created);

        ModulebookResponse response = controller.create(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.studyProgramName()).isEqualTo("Architektur");
        assertThat(response.degreeType()).isEqualTo("BACHELOR");
    }

    @Test
    void deletePassesConfirmationToService() {
        controller.delete(10L, "DELETE");

        verify(modulebookService).delete(10L, "DELETE");
    }

    @Test
    void getModulesLoadsModulesForSelectedModulebook() {
        Modulebook modulebook = new Modulebook(10L, "Architektur", "BACHELOR", null, null);
        Module module = new Module(
                100L,
                10L,
                "B01",
                "Test Module",
                new BigDecimal("5.0"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        ModuleResponse moduleView = new ModuleResponse(
                100L,
                10L,
                "B01",
                "Test Module",
                new BigDecimal("5.0"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of(),
                null
        );

        when(modulebookService.getById(10L)).thenReturn(modulebook);
        when(moduleService.getByModulebookId(10L)).thenReturn(List.of(module));
        when(moduleService.getModuleView(100L)).thenReturn(moduleView);

        List<ModuleResponse> response = controller.getModules(10L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(100L);
        assertThat(response.get(0).modulebookId()).isEqualTo(10L);
        assertThat(response.get(0).moduleNumber()).isEqualTo("B01");
        verify(modulebookService).getById(10L);
        verify(moduleService).getByModulebookId(10L);
        verify(moduleService).getModuleView(100L);
    }
}
