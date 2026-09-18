package de.bht.modulebook.modulebook.dto;

import java.time.Instant;

public record ModulebookTagResponse(
        Long id,
        Long studyProgramId,
        String tagMessage,
        String snapshotJson,
        Instant createdAt,
        String sourceType,
        Long sourceChangeSetId
) {
}
