package de.bht.modulebook.pdfimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.config.PdfImportProperties;
import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.modulebook.ModulebookTagService;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesRequest;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesResponse;
import de.bht.modulebook.studyprogram.StudyProgram;
import de.bht.modulebook.studyprogram.StudyProgramService;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSet;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;
import de.bht.modulebook.teachingform.TeachingForm;
import de.bht.modulebook.teachingform.TeachingFormRepository;
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

    @Mock
    private StudyProgramChangeSetService changeSetService;

    @Mock
    private TeachingFormRepository teachingFormRepository;

    @Mock
    private ModulebookTagService modulebookTagService;

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
                studyProgramService,
                changeSetService,
                teachingFormRepository,
                modulebookTagService
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
        mapped.setModuleNumber("B01");
        mapped.setName("Test Module");
        mapped.setCredits(new BigDecimal("5.0"));
        mapped.setDescription("description");
        ModuleWorkload workload = new ModuleWorkload();
        workload.setRawText("68 Stunden Präsenz (2 SWS SU + 2 SWS Ü)");

        // ModuleService returns the saved row, including database id and timestamps.
        Module created = new Module(
                1L,
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

        // Define how the mocked collaborators should behave for this example.
        when(importedModuleMapper.moduleNumber(imported)).thenReturn("B01");
        when(importedModuleMapper.moduleId(imported)).thenReturn(null);
        StudyProgram studyProgram = new StudyProgram(
                10L,
                "Architektur",
                "BACHELOR",
                LocalDate.parse("2024-11-25"),
                LocalDate.parse("2016-06-14"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        when(studyProgramService.getById(10L)).thenReturn(studyProgram);
        when(changeSetService.create(10L, StudyProgramChangeSetService.SOURCE_PDF_IMPORT, "Re-import Architektur BACHELOR PDF"))
                .thenReturn(new StudyProgramChangeSet(99L, 10L, StudyProgramChangeSetService.SOURCE_PDF_IMPORT, "Re-import Architektur BACHELOR PDF", null));
        when(teachingFormRepository.findAll()).thenReturn(List.of(teachingForm(1L, "SU"), teachingForm(2L, "UE")));
        when(importedModuleMapper.toModule(imported, "Architektur", "BACHELOR")).thenReturn(mapped);
        when(importedModuleMapper.toWorkload(imported, java.util.Map.of("SU", 1L, "UE", 2L))).thenReturn(workload);
        when(moduleService.findByStudyProgramIdAndModuleNumber(10L, "B01")).thenReturn(Optional.empty());
        when(moduleService.create(mapped, workload, List.of(), ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON, 99L)).thenReturn(created);

        // This is the request body shape sent to POST /api/pdf-import/modules.
        SaveImportedModulesRequest request = new SaveImportedModulesRequest(
                10L,
                LocalDate.parse("2024-11-25"),
                LocalDate.parse("2016-06-14"),
                List.of(imported)
        );

        // Act: run the save workflow.
        SaveImportedModulesResponse response = service.saveImportedModules(request);

        // Assert: the service reports one successful creation and no row errors.
        assertThat(response.requestedCount()).isEqualTo(1);
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.errorCount()).isZero();
        assertThat(response.changeSetId()).isEqualTo(99L);
        assertThat(response.createdModules()).hasSize(1);
        assertThat(response.createdModules().get(0).id()).isEqualTo(1L);
        assertThat(response.createdModules().get(0).studyProgramId()).isEqualTo(10L);
        assertThat(response.createdModules().get(0).moduleNumber()).isEqualTo("B01");
        assertThat(response.errors()).isEmpty();
        verify(moduleService).create(mapped, workload, List.of(), ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON, 99L);
        assertThat(mapped.getStudyProgramId()).isEqualTo(10L);
        assertThat(mapped.getModuleStatus()).isEqualTo("ACTIVE");
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
        mapped.setModuleNumber("B01");

        // Simulate a save failure, such as a duplicate module code from the database.
        when(importedModuleMapper.moduleNumber(imported)).thenReturn("B01");
        when(importedModuleMapper.moduleId(imported)).thenReturn(null);
        StudyProgram studyProgram = new StudyProgram(
                10L,
                "Architektur",
                "BACHELOR",
                LocalDate.parse("2024-11-25"),
                LocalDate.parse("2016-06-14"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        when(studyProgramService.getById(10L)).thenReturn(studyProgram);
        when(changeSetService.create(10L, StudyProgramChangeSetService.SOURCE_PDF_IMPORT, "Re-import Architektur BACHELOR PDF"))
                .thenReturn(new StudyProgramChangeSet(99L, 10L, StudyProgramChangeSetService.SOURCE_PDF_IMPORT, "Re-import Architektur BACHELOR PDF", null));
        when(teachingFormRepository.findAll()).thenReturn(List.of());
        when(importedModuleMapper.toModule(imported, "Architektur", "BACHELOR")).thenReturn(mapped);
        when(importedModuleMapper.toWorkload(imported, java.util.Map.of())).thenReturn(null);
        when(moduleService.findByStudyProgramIdAndModuleNumber(10L, "B01")).thenReturn(Optional.empty());
        when(moduleService.create(mapped, null, List.of(), ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON, 99L))
                .thenThrow(new IllegalArgumentException("Duplicate module code"));

        SaveImportedModulesRequest request = new SaveImportedModulesRequest(
                10L,
                LocalDate.parse("2024-11-25"),
                LocalDate.parse("2016-06-14"),
                List.of(imported)
        );

        // Act: the service should catch the row failure instead of throwing it.
        SaveImportedModulesResponse response = service.saveImportedModules(request);

        // Assert: no module was created, and the failed row appears in errors.
        assertThat(response.requestedCount()).isEqualTo(1);
        assertThat(response.createdCount()).isZero();
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.changeSetId()).isEqualTo(99L);
        assertThat(response.createdModules()).isEmpty();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).index()).isZero();
        assertThat(response.errors().get(0).moduleNumber()).isEqualTo("B01");
        assertThat(response.errors().get(0).message()).contains("Duplicate module code");
    }

    @Test
    void marksExistingModulesMissingWhenTheyAreNotInReImport() throws Exception {
        JsonNode importedB01 = objectMapper.readTree("""
                {
                  "module_number": "B01",
                  "title": "Module B01",
                  "credits": 5.0
                }
                """);
        JsonNode importedB03 = objectMapper.readTree("""
                {
                  "module_number": "B03",
                  "title": "Module B03",
                  "credits": 5.0
                }
                """);

        Module existingB01 = module(1L, "B01", "Old B01", "ACTIVE");
        Module existingB02 = module(2L, "B02", "Old B02", "ACTIVE");
        Module existingB03 = module(3L, "B03", "Old B03", "ACTIVE");
        Module existingB04 = module(4L, "B04", "Deactive B04", ModuleService.DEACTIVE_STATUS);
        Module mappedB01 = module(null, "B01", "Module B01", "Pflichtmodul");
        Module mappedB03 = module(null, "B03", "Module B03", "Pflichtmodul");
        Module savedB01 = module(1L, "B01", "Module B01", "ACTIVE");
        Module savedB03 = module(3L, "B03", "Module B03", "ACTIVE");
        Module savedB02 = module(2L, "B02", "Old B02", "MISSING_FROM_IMPORT");
        StudyProgram studyProgram = new StudyProgram(
                10L,
                "Architektur",
                "BACHELOR",
                LocalDate.parse("2024-11-25"),
                LocalDate.parse("2016-06-14"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(studyProgramService.getById(10L)).thenReturn(studyProgram);
        when(changeSetService.create(10L, StudyProgramChangeSetService.SOURCE_PDF_IMPORT, "Re-import Architektur BACHELOR PDF"))
                .thenReturn(new StudyProgramChangeSet(99L, 10L, StudyProgramChangeSetService.SOURCE_PDF_IMPORT, "Re-import Architektur BACHELOR PDF", null));
        when(teachingFormRepository.findAll()).thenReturn(List.of());
        when(moduleService.getByStudyProgramId(10L)).thenReturn(List.of(existingB01, existingB02, existingB03, existingB04));
        when(importedModuleMapper.moduleNumber(importedB01)).thenReturn("B01");
        when(importedModuleMapper.moduleNumber(importedB03)).thenReturn("B03");
        when(importedModuleMapper.moduleId(importedB01)).thenReturn(null);
        when(importedModuleMapper.moduleId(importedB03)).thenReturn(null);
        when(importedModuleMapper.toModule(importedB01, "Architektur", "BACHELOR")).thenReturn(mappedB01);
        when(importedModuleMapper.toModule(importedB03, "Architektur", "BACHELOR")).thenReturn(mappedB03);
        when(importedModuleMapper.toWorkload(importedB01, java.util.Map.of())).thenReturn(null);
        when(importedModuleMapper.toWorkload(importedB03, java.util.Map.of())).thenReturn(null);
        when(moduleService.findByStudyProgramIdAndModuleNumber(10L, "B01")).thenReturn(Optional.of(existingB01));
        when(moduleService.findByStudyProgramIdAndModuleNumber(10L, "B03")).thenReturn(Optional.of(existingB03));
        when(moduleService.update(1L, mappedB01, null, List.of(), "before PDF re-import", 99L)).thenReturn(savedB01);
        when(moduleService.update(3L, mappedB03, null, List.of(), "before PDF re-import", 99L)).thenReturn(savedB03);
        when(moduleService.update(eq(2L), org.mockito.ArgumentMatchers.any(Module.class), eq("before marking missing from PDF re-import"), eq(99L)))
                .thenReturn(savedB02);

        SaveImportedModulesResponse response = service.saveImportedModules(new SaveImportedModulesRequest(
                10L,
                LocalDate.parse("2024-11-25"),
                LocalDate.parse("2016-06-14"),
                List.of(importedB01, importedB03)
        ));

        assertThat(response.requestedCount()).isEqualTo(2);
        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(response.errorCount()).isZero();
        assertThat(mappedB01.getModuleStatus()).isEqualTo("ACTIVE");
        assertThat(mappedB03.getModuleStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<Module> missingModule = ArgumentCaptor.forClass(Module.class);
        verify(moduleService).update(eq(2L), missingModule.capture(), eq("before marking missing from PDF re-import"), eq(99L));
        assertThat(missingModule.getValue().getModuleNumber()).isEqualTo("B02");
        assertThat(missingModule.getValue().getModuleStatus()).isEqualTo("ACTIVE");
        assertThat(missingModule.getValue().getModuleNote()).isEqualTo("MISSING_FROM_IMPORT");

        ArgumentCaptor<Module> missingDeactiveModule = ArgumentCaptor.forClass(Module.class);
        verify(moduleService).update(eq(4L), missingDeactiveModule.capture(), eq("before marking missing from PDF re-import"), eq(99L));
        assertThat(missingDeactiveModule.getValue().getModuleNumber()).isEqualTo("B04");
        assertThat(missingDeactiveModule.getValue().getModuleStatus()).isEqualTo("DEACTIVE");
        assertThat(missingDeactiveModule.getValue().getModuleNote()).isEqualTo("MISSING_FROM_IMPORT");
    }

    @Test
    void rejectsEmptyModuleList() {
        // Arrange: saving nothing is a bad request because there is no work to perform.
        SaveImportedModulesRequest request = new SaveImportedModulesRequest(
                10L,
                LocalDate.parse("2024-11-25"),
                LocalDate.parse("2016-06-14"),
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

    private Module module(Long id, String moduleNumber, String name, String moduleStatus) {
        Module module = new Module();
        module.setId(id);
        module.setStudyProgramId(10L);
        module.setModuleNumber(moduleNumber);
        module.setName(name);
        module.setCredits(new BigDecimal("5.0"));
        module.setDescription("description " + moduleNumber);
        module.setModuleStatus(moduleStatus);
        return module;
    }

    private TeachingForm teachingForm(Long id, String code) {
        TeachingForm teachingForm = new TeachingForm();
        teachingForm.setId(id);
        teachingForm.setCode(code);
        return teachingForm;
    }
}
