package de.bht.modulebook.modulebook;

import de.bht.modulebook.modulebook.dto.ModulebookVersionResponse;

public class ModulebookVersionMapper {
    public static ModulebookVersionResponse toResponse(ModulebookVersion version) {
        return new ModulebookVersionResponse(
                version.getId(),
                version.getModulebookId(),
                version.getVersionNumber(),
                version.getVersionMessage(),
                version.getSnapshotJson(),
                version.getCreatedAt(),
                version.getSourceType(),
                version.getSourceChangeSetId(),
                version.getModulebookReleaseDate(),
                version.getStudyExaminationRegulationsDate()
        );
    }
}
