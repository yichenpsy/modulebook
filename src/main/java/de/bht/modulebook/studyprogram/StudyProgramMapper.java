package de.bht.modulebook.studyprogram;

import de.bht.modulebook.modulebook.Modulebook;
import de.bht.modulebook.studyprogram.dto.StudyProgramResponse;

public class StudyProgramMapper {

    public static StudyProgramResponse toResponse(Modulebook modulebook) {
        return new StudyProgramResponse(
                modulebook.getId(),
                modulebook.getStudyProgramName(),
                modulebook.getDegreeType(),
                modulebook.getCreatedAt()
        );
    }
}
