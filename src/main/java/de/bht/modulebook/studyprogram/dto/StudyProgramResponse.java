package de.bht.modulebook.studyprogram.dto;

import java.time.Instant;

public record StudyProgramResponse(
        Long id,
        String name,
        String degreeType,
        Instant createdAt,
        Instant updatedAt
) {
}
