package de.bht.modulebook.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
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
import de.bht.modulebook.module.version.ModuleVersionService;
import de.bht.modulebook.module.workload.ModuleWorkloadRepository;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSet;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;

@ExtendWith(MockitoExtension.class)
class ModuleServiceTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private ModuleWorkloadRepository workloadRepository;

    @Mock
    private ModuleTeachingFormRepository moduleTeachingFormRepository;

    @Mock
    private ModuleVersionService versionService;

    @Mock
    private StudyProgramChangeSetService changeSetService;

    private ModuleService service;

    @BeforeEach
    void setUp() {
        service = new ModuleService(
                moduleRepository,
                workloadRepository,
                moduleTeachingFormRepository,
                versionService,
                changeSetService
        );
    }

    @Test
    void createSavesInitialVersionAfterModuleIsCreated() {
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

        InOrder inOrder = inOrder(changeSetService, moduleRepository, moduleTeachingFormRepository, versionService);
        inOrder.verify(changeSetService).create(10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, ModuleService.MANUAL_INITIAL_VERSION_REASON);
        inOrder.verify(moduleRepository).insert(request);
        inOrder.verify(moduleTeachingFormRepository).replaceForModule(100L, List.of());
        inOrder.verify(versionService).saveSnapshot(100L, ModuleService.MANUAL_INITIAL_VERSION_REASON, 99L);
    }

    @Test
    void createFromPdfImportSavesPdfImportVersionReason() {
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

        InOrder inOrder = inOrder(changeSetService, moduleRepository, moduleTeachingFormRepository, versionService);
        inOrder.verify(changeSetService).create(10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON);
        inOrder.verify(moduleRepository).insert(request);
        inOrder.verify(moduleTeachingFormRepository).replaceForModule(100L, List.of());
        inOrder.verify(versionService).saveSnapshot(100L, ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON, 99L);
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

        InOrder inOrder = inOrder(changeSetService, moduleRepository, moduleTeachingFormRepository, versionService);
        inOrder.verify(changeSetService).create(10L, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE, "created module manually");
        inOrder.verify(moduleRepository).insert(request);
        inOrder.verify(moduleTeachingFormRepository).replaceForModule(100L, List.of());
        inOrder.verify(versionService).saveSnapshot(100L, "created module manually", 99L);
    }

    @Test
    void deleteSavesVersionBeforeMarkingModuleDeleted() {
        when(moduleRepository.updateModuleStatus(100L, ModuleService.DELETED_STATUS)).thenReturn(true);

        service.delete(100L);

        InOrder inOrder = inOrder(versionService, moduleRepository);
        inOrder.verify(versionService).saveSnapshotInNewChangeSet(100L, "before delete", StudyProgramChangeSetService.SOURCE_DELETE);
        inOrder.verify(moduleRepository).updateModuleStatus(100L, ModuleService.DELETED_STATUS);
    }
}
