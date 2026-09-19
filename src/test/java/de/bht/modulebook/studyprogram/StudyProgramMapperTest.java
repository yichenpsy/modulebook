package de.bht.modulebook.studyprogram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import de.bht.modulebook.studyprogram.dto.StudyProgramRequest;
import de.bht.modulebook.studyprogram.dto.StudyProgramResponse;

class StudyProgramMapperTest {

    @Test
    void mapsRequestToEntity() {
        // These values are the same shape the frontend sends as JSON.
        StudyProgramRequest request = new StudyProgramRequest("Architektur", "BACHELOR");

        StudyProgram studyProgram = StudyProgramMapper.toEntity(request);

        assertThat(studyProgram.getName()).isEqualTo("Architektur");
        assertThat(studyProgram.getDegreeType()).isEqualTo("BACHELOR");
    }

    @Test
    void mapsEntityToResponse() {
        // id and timestamps normally come from the database after insert/select.
        StudyProgram studyProgram = new StudyProgram(
                10L,
                "Architektur",
                "BACHELOR",
                LocalDate.parse("2024-11-25"),
                LocalDate.parse("2016-06-14"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );

        StudyProgramResponse response = StudyProgramMapper.toResponse(studyProgram);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Architektur");
        assertThat(response.degreeType()).isEqualTo("BACHELOR");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
    }
}
