package de.bht.modulebook.modulebook.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ModulebookTagResponse(
        Long id,
        Long studyProgramId,
        String tagMessage,
        String snapshotJson,
        Instant createdAt,
        String sourceType,
        Long sourceChangeSetId,
        LocalDate modulebookReleaseDate,
        LocalDate studyExaminationRegulationsDate
) {
}
