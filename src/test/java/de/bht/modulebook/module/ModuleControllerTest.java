package de.bht.modulebook.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.bht.modulebook.module.dto.ModuleRequest;
import de.bht.modulebook.module.dto.ModuleResponse;

@ExtendWith(MockitoExtension.class)
class ModuleControllerTest {

    @Mock
    private ModuleService moduleService;

    private ModuleController controller;

    @BeforeEach
    void setUp() {
        controller = new ModuleController(moduleService);
    }

    @Test
    void createPassesNewModuleRequestToService() {
        Module created = new Module(
                100L,
                10L,
                "B01",
                "New Module",
                new BigDecimal("5.0"),
                "Pflichtmodul",
                "Prof. Example",
                "",
                "",
                "",
                "",
                "",
                "DRAFT",
                "Wintersemester",
                "Klausur",
                "contents",
                "",
                "",
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        ModuleRequest request = new ModuleRequest(
                10L,
                "B01",
                "New Module",
                new BigDecimal("5.0"),
                "Pflichtmodul",
                "Prof. Example",
                "",
                "",
                "",
                "",
                "",
                "DRAFT",
                null,
                "Wintersemester",
                "contents",
                "",
                "",
                null,
                List.of(),
                null,
                "Klausur",
                "created module manually",
                List.of()
        );

        when(moduleService.create(any(Module.class), any(), eq(List.of()), eq("created module manually")))
                .thenReturn(created);
        when(moduleService.getModuleView(100L)).thenReturn(ModuleMapper.toResponse(created));

        ModuleResponse response = controller.create(request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.modulebookId()).isEqualTo(10L);
        assertThat(response.moduleNumber()).isEqualTo("B01");
        assertThat(response.title()).isEqualTo("New Module");
        verify(moduleService).create(any(Module.class), isNull(), eq(List.of()), eq("created module manually"));
    }
}
