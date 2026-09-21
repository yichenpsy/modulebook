package de.bht.modulebook.modulebook.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ModulebookResponse(
        Long id,
        Long studyProgramId,
        String studyProgramName,
        String degreeType,
        String title,
        String status,
        LocalDate modulebookReleaseDate,
        LocalDate studyExaminationRegulationsDate,
        Instant createdAt,
        Instant updatedAt
) {
}
