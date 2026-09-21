package de.bht.modulebook.studyprogram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import de.bht.modulebook.modulebook.Modulebook;
import de.bht.modulebook.studyprogram.dto.StudyProgramResponse;

class StudyProgramMapperTest {

    @Test
    void mapsModulebookToCompatibilityResponse() {
        // id and timestamps normally come from the database after insert/select.
        Modulebook modulebook = new Modulebook(
                10L,
                "Architektur",
                "BACHELOR",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );

        StudyProgramResponse response = StudyProgramMapper.toResponse(modulebook);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Architektur");
        assertThat(response.degreeType()).isEqualTo("BACHELOR");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
