package de.bht.modulebook.studyprogram;

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
import de.bht.modulebook.modulebook.Modulebook;
import de.bht.modulebook.modulebook.ModulebookService;
import de.bht.modulebook.modulebook.dto.CreateModulebookRequest;
import de.bht.modulebook.modulebook.dto.ModulebookResponse;
import de.bht.modulebook.studyprogram.dto.StudyProgramRequest;
import de.bht.modulebook.studyprogram.dto.StudyProgramResponse;

@ExtendWith(MockitoExtension.class)
class StudyProgramControllerTest {

    @Mock
    private ModulebookService modulebookService;

    @Mock
    private ModuleService moduleService;

    private StudyProgramController controller;

    @BeforeEach
    void setUp() {
        controller = new StudyProgramController(modulebookService, moduleService);
    }

    @Test
    void getAllReturnsStudyProgramResponses() {
        Modulebook modulebook = new Modulebook(
                10L,
                "Architektur",
                "BACHELOR",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
        when(modulebookService.getAll()).thenReturn(List.of(modulebook));

        List<StudyProgramResponse> response = controller.getAll();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(10L);
        assertThat(response.get(0).name()).isEqualTo("Architektur");
        assertThat(response.get(0).degreeType()).isEqualTo("BACHELOR");
    }

    @Test
    void createPassesRequestDataToService() {
        ModulebookResponse created = new ModulebookResponse(
                10L,
                10L,
                "Architektur",
                "BACHELOR",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
        when(modulebookService.create(new CreateModulebookRequest("Architektur", "BACHELOR"))).thenReturn(created);

        StudyProgramResponse response = controller.create(new StudyProgramRequest("Architektur", "BACHELOR"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Architektur");
        assertThat(response.degreeType()).isEqualTo("BACHELOR");
    }

    @Test
    void getModulesLoadsModulesForSelectedStudyProgram() {
        Modulebook modulebook = new Modulebook(10L, "Architektur", "BACHELOR", null, null);
        Module module = new Module(
                100L,
                10L,
                "B01",
                "Test Module",
                new BigDecimal("5.0"),
                "description",
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
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(modulebookService.getById(10L)).thenReturn(modulebook);
        when(moduleService.getByStudyProgramId(10L)).thenReturn(List.of(module));

        List<ModuleResponse> response = controller.getModules(10L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(100L);
        assertThat(response.get(0).studyProgramId()).isEqualTo(10L);
        assertThat(response.get(0).moduleNumber()).isEqualTo("B01");
        verify(modulebookService).getById(10L);
        verify(moduleService).getByStudyProgramId(10L);
    }

}
