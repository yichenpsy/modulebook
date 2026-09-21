package de.bht.modulebook.modulebook.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ModulebookVersionResponse(
        Long id,
        Long modulebookId,
        Integer versionNumber,
        String versionMessage,
        String snapshotJson,
        Instant createdAt,
        String sourceType,
        Long sourceChangeSetId,
        LocalDate modulebookReleaseDate,
        LocalDate studyExaminationRegulationsDate
) {
}
