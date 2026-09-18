package de.bht.modulebook.modulebook;

import de.bht.modulebook.modulebook.dto.ModulebookTagResponse;

public class ModulebookTagMapper {
    public static ModulebookTagResponse toResponse(ModulebookTag tag) {
        return new ModulebookTagResponse(
                tag.getId(),
                tag.getStudyProgramId(),
                tag.getTagMessage(),
                tag.getSnapshotJson(),
                tag.getCreatedAt(),
                tag.getSourceType(),
                tag.getSourceChangeSetId()
        );
    }
}
