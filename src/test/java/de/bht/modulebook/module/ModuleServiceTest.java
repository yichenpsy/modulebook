package de.bht.modulebook.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.bht.modulebook.module.teachingform.ModuleTeachingFormRepository;
import de.bht.modulebook.module.version.ModuleChangeLogService;
import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.module.workload.ModuleWorkloadRepository;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSet;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;
import de.bht.modulebook.teachingform.TeachingForm;

@ExtendWith(MockitoExtension.class)
class ModuleServiceTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private ModuleWorkloadRepository workloadRepository;

    @Mock
    private ModuleTeachingFormRepository moduleTeachingFormRepository;

    @Mock
    private StudyProgramChangeSetService changeSetService;

    @Mock
    private ModuleChangeLogService changeLogService;

    private ModuleService service;

    @BeforeEach
    void setUp() {
        service = new ModuleService(
                moduleRepository,
                workloadRepository,
                moduleTeachingFormRepository,
                changeSetService,
                changeLogService
        );
    }

    @Test
    void createCreatesInitialChangeSetAfterModuleIsCreated() {
        Module request = new Module();
        request.setStudyProgramId(10L);
        request.setModuleNumber("B01");
        request.setName("New Module");
        request.setCredits(new BigDecimal("5.0"));

        Module inserted = new Module();
        inserted.setId(100L);
        inserted.setStudyProgramId(10L);
        inserted.setModuleNumber("B01");
        inserted.setName("New Module");
        inserted.setCredits(new BigDecimal("5.0"));

        when(moduleRepository.insert(request)).thenReturn(inserted);
        when(moduleRepository.findById(100L)).thenReturn(Optional.of(inserted));
        when(changeSetService.create(10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, ModuleService.MANUAL_INITIAL_VERSION_REASON))
                .thenReturn(new StudyProgramChangeSet(99L, 10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, ModuleService.MANUAL_INITIAL_VERSION_REASON, null));

        Module result = service.create(request, null, List.of());

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(request.getModuleStatus()).isEqualTo(ModuleService.ACTIVE_STATUS);

        InOrder inOrder = inOrder(changeSetService, moduleRepository, moduleTeachingFormRepository);
        inOrder.verify(changeSetService).create(10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, ModuleService.MANUAL_INITIAL_VERSION_REASON);
        inOrder.verify(moduleRepository).insert(request);
        inOrder.verify(moduleTeachingFormRepository).replaceForModule(100L, List.of());
    }

    @Test
    void createFromPdfImportCreatesPdfImportChangeSetReason() {
        Module request = new Module();
        request.setStudyProgramId(10L);
        request.setModuleNumber("B01");
        request.setName("Imported Module");
        request.setCredits(new BigDecimal("5.0"));

        Module inserted = new Module();
        inserted.setId(100L);
        inserted.setStudyProgramId(10L);
        inserted.setModuleNumber("B01");
        inserted.setName("Imported Module");
        inserted.setCredits(new BigDecimal("5.0"));

        when(moduleRepository.insert(request)).thenReturn(inserted);
        when(moduleRepository.findById(100L)).thenReturn(Optional.of(inserted));
        when(changeSetService.create(10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON))
                .thenReturn(new StudyProgramChangeSet(99L, 10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON, null));

        Module result = service.createFromPdfImport(request);

        assertThat(result.getId()).isEqualTo(100L);

        InOrder inOrder = inOrder(changeSetService, moduleRepository, moduleTeachingFormRepository);
        inOrder.verify(changeSetService).create(10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON);
        inOrder.verify(moduleRepository).insert(request);
        inOrder.verify(moduleTeachingFormRepository).replaceForModule(100L, List.of());
    }

    @Test
    void createUsesProvidedInitialVersionReason() {
        Module request = new Module();
        request.setStudyProgramId(10L);
        request.setModuleNumber("B01");
        request.setName("New Module");
        request.setCredits(new BigDecimal("5.0"));

        Module inserted = new Module();
        inserted.setId(100L);
        inserted.setStudyProgramId(10L);
        inserted.setModuleNumber("B01");
        inserted.setName("New Module");
        inserted.setCredits(new BigDecimal("5.0"));

        when(moduleRepository.insert(request)).thenReturn(inserted);
        when(moduleRepository.findById(100L)).thenReturn(Optional.of(inserted));
        when(changeSetService.create(10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, "created module manually"))
                .thenReturn(new StudyProgramChangeSet(99L, 10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, "created module manually", null));

        Module result = service.create(request, null, List.of(), "created module manually");

        assertThat(result.getId()).isEqualTo(100L);

        InOrder inOrder = inOrder(changeSetService, moduleRepository, moduleTeachingFormRepository);
        inOrder.verify(changeSetService).create(10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, "created module manually");
        inOrder.verify(moduleRepository).insert(request);
        inOrder.verify(moduleTeachingFormRepository).replaceForModule(100L, List.of());
    }

    @Test
    void deleteRecordsStudyProgramChangeSetThenPhysicallyRemovesModule() {
        Module oldModule = new Module();
        oldModule.setId(100L);
        oldModule.setStudyProgramId(10L);
        oldModule.setModuleNumber("B01");
        oldModule.setName("Old Module");
        oldModule.setCredits(new BigDecimal("5.0"));
        oldModule.setModuleStatus(ModuleService.ACTIVE_STATUS);

        when(moduleRepository.findById(100L)).thenReturn(Optional.of(oldModule));
        when(changeSetService.create(10L, StudyProgramChangeSetService.SOURCE_DELETE, "deleted module B01 Old Module"))
                .thenReturn(new StudyProgramChangeSet(77L, 10L, StudyProgramChangeSetService.SOURCE_DELETE, "deleted module B01 Old Module", null));
        when(moduleRepository.delete(100L)).thenReturn(true);

        service.delete(100L);

        InOrder inOrder = inOrder(moduleRepository, changeSetService);
        inOrder.verify(moduleRepository).findById(100L);
        inOrder.verify(changeSetService).create(10L, StudyProgramChangeSetService.SOURCE_DELETE, "deleted module B01 Old Module");
        inOrder.verify(moduleRepository).delete(100L);
        verifyNoInteractions(changeLogService);
    }

    @Test
    void updateRecordsFieldChangesWithSavedDatabaseState() {
        Module oldModule = new Module();
        oldModule.setId(100L);
        oldModule.setStudyProgramId(10L);
        oldModule.setModuleNumber("B01");
        oldModule.setName("Old Module");
        oldModule.setCredits(new BigDecimal("5.0"));

        Module request = new Module();
        request.setStudyProgramId(10L);
        request.setModuleNumber("B01");
        request.setName("Updated Module");
        request.setCredits(new BigDecimal("6.0"));

        ModuleWorkload oldWorkload = new ModuleWorkload();
        ModuleWorkload requestWorkload = new ModuleWorkload();
        ModuleWorkload savedWorkload = new ModuleWorkload();
        savedWorkload.setId(252L);
        savedWorkload.setModuleId(100L);
        TeachingForm savedTeachingForm = new TeachingForm();
        savedTeachingForm.setId(1L);

        when(moduleRepository.findById(100L)).thenReturn(Optional.of(oldModule), Optional.of(request));
        when(workloadRepository.findByModuleId(100L)).thenReturn(Optional.of(oldWorkload), Optional.of(savedWorkload));
        when(moduleTeachingFormRepository.findByModuleId(100L)).thenReturn(List.of(), List.of(savedTeachingForm));
        when(changeSetService.create(10L, StudyProgramChangeSetService.SOURCE_MANUAL_EDIT, "changed name and credits"))
                .thenReturn(new StudyProgramChangeSet(55L, 10L, StudyProgramChangeSetService.SOURCE_MANUAL_EDIT, "changed name and credits", null));
        when(moduleRepository.update(100L, request)).thenReturn(true);

        Module result = service.update(100L, request, requestWorkload, List.of(), "changed name and credits");

        assertThat(result).isSameAs(request);

        InOrder inOrder = inOrder(moduleRepository, changeSetService, changeLogService);
        inOrder.verify(moduleRepository).findById(100L);
        inOrder.verify(changeSetService).create(10L, StudyProgramChangeSetService.SOURCE_MANUAL_EDIT, "changed name and credits");
        inOrder.verify(moduleRepository).update(100L, request);
        inOrder.verify(changeLogService).saveChanges(55L, 100L, oldModule, request, oldWorkload, savedWorkload, List.of(), List.of(1L));
    }

    @Test
    void updateWithEmptyChangedFieldPathsDoesNotCreateChangeSetOrLog() {
        Module oldModule = new Module();
        oldModule.setId(100L);
        oldModule.setStudyProgramId(10L);
        oldModule.setModuleNumber("B01");
        oldModule.setName("Old Module");
        oldModule.setCredits(new BigDecimal("5.0"));

        Module request = new Module();
        request.setStudyProgramId(10L);
        request.setModuleNumber("B01");
        request.setName("Old Module");
        request.setCredits(new BigDecimal("5.0"));

        when(moduleRepository.findById(100L)).thenReturn(Optional.of(oldModule));

        Module result = service.update(100L, request, null, null, "updated module without field changes", List.of());

        assertThat(result).isSameAs(oldModule);
        verify(moduleRepository).findById(100L);
        verifyNoInteractions(workloadRepository, moduleTeachingFormRepository, changeSetService, changeLogService);
    }
}
