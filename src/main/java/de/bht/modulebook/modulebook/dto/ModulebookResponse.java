package de.bht.modulebook.modulebook.dto;

import java.time.Instant;

public record ModulebookResponse(
        Long id,
        Long studyProgramId,
        String studyProgramName,
        String degreeType,
        Instant createdAt,
        Instant updatedAt
) {
}
