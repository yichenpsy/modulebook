package de.bht.modulebook.modulebook;

import de.bht.modulebook.modulebook.dto.ModulebookResponse;

public class ModulebookMapper {
    public static ModulebookResponse toResponse(Modulebook modulebook) {
        return new ModulebookResponse(
                modulebook.getId(),
                modulebook.getStudyProgramName(),
                modulebook.getDegreeType(),
                modulebook.getCreatedAt(),
                modulebook.getUpdatedAt()
        );
    }
}
