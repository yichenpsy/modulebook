package de.bht.modulebook.module.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleRepository;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.teachingform.ModuleTeachingFormRepository;
import de.bht.modulebook.module.workload.ModuleWorkloadRepository;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSet;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ModuleVersionServiceTest {

    @Mock
    private ModuleVersionRepository versionRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private ModuleWorkloadRepository workloadRepository;

    @Mock
    private ModuleTeachingFormRepository teachingFormRepository;

    @Mock
    private StudyProgramChangeSetService changeSetService;

    private ModuleVersionService service;

    @BeforeEach
    void setUp() {
        service = new ModuleVersionService(
                versionRepository,
                moduleRepository,
                workloadRepository,
                teachingFormRepository,
                changeSetService,
                JsonMapper.builder().build()
        );
    }

    @Test
    void getHistoryCreatesInitialVersionWhenModuleHasNoVersionsYet() {
        Module module = new Module();
        module.setId(528L);
        module.setStudyProgramId(10L);
        module.setModuleNumber("B01");
        module.setName("New Module");
        module.setCredits(new BigDecimal("5.0"));

        ModuleVersion initialVersion = new ModuleVersion(
                1L,
                528L,
                1,
                "{\"module\":{\"id\":528}}",
                ModuleService.MANUAL_INITIAL_VERSION_REASON,
                Instant.parse("2026-08-25T10:20:00Z")
        );

        when(versionRepository.findByModuleId(528L)).thenReturn(List.of(), List.of(initialVersion));
        when(moduleRepository.findById(528L)).thenReturn(Optional.of(module));
        when(teachingFormRepository.findByModuleId(528L)).thenReturn(List.of());
        when(workloadRepository.findByModuleId(528L)).thenReturn(Optional.empty());
        when(versionRepository.nextVersionNumber(528L)).thenReturn(1);

        List<ModuleVersion> history = service.getHistory(528L);

        assertThat(history).containsExactly(initialVersion);
        verify(versionRepository).insert(
                org.mockito.ArgumentMatchers.eq(528L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(ModuleService.MANUAL_INITIAL_VERSION_REASON)
        );
    }

    @Test
    void getHistoryRepairsOldInitialVersionReason() {
        Module module = new Module();
        module.setId(528L);
        module.setStudyProgramId(10L);
        module.setModuleNumber("B01");
        module.setName("New Module");
        module.setCredits(new BigDecimal("5.0"));

        ModuleVersion oldInitialVersion = new ModuleVersion(
                1L,
                528L,
                1,
                "{\"module\":{\"id\":528}}",
                "initial version",
                Instant.parse("2026-08-25T10:20:00Z")
        );
        ModuleVersion repairedInitialVersion = new ModuleVersion(
                1L,
                528L,
                1,
                "{\"module\":{\"id\":528}}",
                ModuleService.MANUAL_INITIAL_VERSION_REASON,
                Instant.parse("2026-08-25T10:20:00Z")
        );

        when(versionRepository.findByModuleId(528L))
                .thenReturn(List.of(oldInitialVersion), List.of(repairedInitialVersion));
        when(moduleRepository.findById(528L)).thenReturn(Optional.of(module));

        List<ModuleVersion> history = service.getHistory(528L);

        assertThat(history).containsExactly(repairedInitialVersion);
        verify(versionRepository).updateReason(528L, 1, ModuleService.MANUAL_INITIAL_VERSION_REASON);
    }

    @Test
    void restoreTurnsDeletedSnapshotBackToActive() {
        Module currentModule = new Module();
        currentModule.setId(528L);
        currentModule.setStudyProgramId(10L);
        currentModule.setModuleNumber("B01");
        currentModule.setName("Deleted Module");
        currentModule.setCredits(new BigDecimal("5.0"));
        currentModule.setModuleStatus(ModuleService.DELETED_STATUS);

        Module restoredModule = new Module();
        restoredModule.setId(528L);
        restoredModule.setStudyProgramId(10L);
        restoredModule.setModuleNumber("B01");
        restoredModule.setName("Deleted Module");
        restoredModule.setCredits(new BigDecimal("5.0"));
        restoredModule.setModuleStatus(ModuleService.ACTIVE_STATUS);

        ModuleVersion deletedVersion = new ModuleVersion(
                1L,
                528L,
                1,
                """
                {"module":{"id":528,"studyProgramId":10,"moduleNumber":"B01","name":"Deleted Module","credits":5.0,"status":"DELETED"}}
                """,
                "deleted snapshot",
                Instant.parse("2026-08-25T10:20:00Z")
        );

        when(versionRepository.findByModuleIdAndVersionNumber(528L, 1)).thenReturn(Optional.of(deletedVersion));
        when(moduleRepository.findById(528L)).thenReturn(Optional.of(currentModule), Optional.of(restoredModule));
        when(changeSetService.create(10L, StudyProgramChangeSetService.SOURCE_RESTORE, "before restore to version 1"))
                .thenReturn(new StudyProgramChangeSet(99L, 10L, StudyProgramChangeSetService.SOURCE_RESTORE, "before restore to version 1", null));
        when(teachingFormRepository.findByModuleId(528L)).thenReturn(List.of());
        when(workloadRepository.findByModuleId(528L)).thenReturn(Optional.empty());
        when(versionRepository.nextVersionNumber(528L)).thenReturn(2);
        when(moduleRepository.update(eq(528L), org.mockito.ArgumentMatchers.any(Module.class))).thenReturn(true);

        Module result = service.restore(528L, 1);

        ArgumentCaptor<Module> restoredCaptor = ArgumentCaptor.forClass(Module.class);
        verify(moduleRepository).update(eq(528L), restoredCaptor.capture());
        assertThat(restoredCaptor.getValue().getModuleStatus()).isEqualTo(ModuleService.ACTIVE_STATUS);
        assertThat(result.getModuleStatus()).isEqualTo(ModuleService.ACTIVE_STATUS);
    }
}
