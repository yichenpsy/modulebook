package de.bht.modulebook.module.version;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.module.workload.PresenceHourComponent;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ModuleChangeLogServiceTest {
    @Mock
    private ModuleChangeRepository repository;

    private ModuleChangeLogService service;

    @BeforeEach
    void setUp() {
        service = new ModuleChangeLogService(repository, JsonMapper.builder().build());
    }

    @Test
    void saveChangesIgnoresWorkloadDatabaseIdsWhenBusinessValuesAreEqual() {
        Module oldModule = module("Old title");
        Module newModule = module("New title");
        ModuleWorkload oldWorkload = workloadWithPersistenceIds();
        ModuleWorkload newWorkload = workloadWithoutPersistenceIds();

        service.saveChanges(
                55L,
                100L,
                oldModule,
                newModule,
                oldWorkload,
                newWorkload,
                List.of(1L),
                List.of(1L)
        );

        verify(repository).insert(eq(55L), eq(100L), eq("module.title"), eq("\"Old title\""), eq("\"New title\""));
        verify(repository, never()).insert(eq(55L), eq(100L), eq("workload"), anyString(), anyString());
        verify(repository, never()).insert(eq(55L), eq(100L), eq("teachingFormIds"), anyString(), anyString());
    }

    @Test
    void saveChangesTreatsNullAndBlankOptionalTextAsEqual() {
        Module oldModule = module("Same title");
        oldModule.setCoordinator(null);

        Module newModule = module("Same title");
        newModule.setCoordinator("   ");

        service.saveChanges(
                55L,
                100L,
                oldModule,
                newModule,
                null,
                null,
                List.of(),
                List.of()
        );

        verifyNoInteractions(repository);
    }

    @Test
    void saveChangesIgnoresRawTextAndComponentOrderWhenWorkloadBusinessValuesAreEqual() {
        Module oldModule = module("Same title");
        Module newModule = module("Same title");

        ModuleWorkload oldWorkload = new ModuleWorkload();
        oldWorkload.setPresenceHours(new BigDecimal("85.0"));
        oldWorkload.setSelfStudyHours(new BigDecimal("65.0"));
        oldWorkload.setRawText("old imported text");
        oldWorkload.setComponents(List.of(
                component(301L, 2L, "UE", "1.0"),
                component(300L, 1L, "SU", "4.0")
        ));

        ModuleWorkload newWorkload = new ModuleWorkload();
        newWorkload.setPresenceHours(new BigDecimal("85.00"));
        newWorkload.setSelfStudyHours(new BigDecimal("65.00"));
        newWorkload.setRawText("");
        newWorkload.setComponents(List.of(
                component(null, 1L, null, "4.00"),
                component(null, 2L, null, "1.00")
        ));

        service.saveChanges(
                55L,
                100L,
                oldModule,
                newModule,
                oldWorkload,
                newWorkload,
                List.of(2L, 1L),
                List.of(1L, 2L)
        );

        verifyNoInteractions(repository);
    }

    private Module module(String name) {
        Module module = new Module();
        module.setId(100L);
        module.setModulebookId(10L);
        module.setModuleNumber("B01");
        module.setTitle(name);
        module.setCredits(new BigDecimal("5.0"));
        return module;
    }

    private ModuleWorkload workloadWithPersistenceIds() {
        ModuleWorkload workload = new ModuleWorkload();
        workload.setId(20L);
        workload.setModuleId(100L);
        workload.setPresenceHours(new BigDecimal("68.0"));
        workload.setSelfStudyHours(new BigDecimal("82.00"));
        workload.setRawText("68 presence, 82 self study");
        workload.setComponents(List.of(component(300L, 1L, "SU", "4.0")));
        return workload;
    }

    private ModuleWorkload workloadWithoutPersistenceIds() {
        ModuleWorkload workload = new ModuleWorkload();
        workload.setPresenceHours(new BigDecimal("68.00"));
        workload.setSelfStudyHours(new BigDecimal("82.0"));
        workload.setRawText("68 presence, 82 self study");
        workload.setComponents(List.of(component(null, 1L, null, "4.00")));
        return workload;
    }

    private PresenceHourComponent component(Long id, Long teachingFormId, String teachingFormCode, String swsAmount) {
        PresenceHourComponent component = new PresenceHourComponent();
        component.setId(id);
        component.setTeachingFormId(teachingFormId);
        component.setTeachingFormCode(teachingFormCode);
        component.setSwsAmount(new BigDecimal(swsAmount));
        return component;
    }
}
