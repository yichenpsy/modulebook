package de.bht.modulebook.modulebook;

import de.bht.modulebook.modulebook.dto.ModulebookResponse;
import de.bht.modulebook.studyprogram.StudyProgram;

public class ModulebookMapper {
    public static ModulebookResponse toResponse(Modulebook modulebook, StudyProgram studyProgram) {
        return new ModulebookResponse(
                modulebook.getId(),
                modulebook.getStudyProgramId(),
                studyProgram.getName(),
                studyProgram.getDegreeType(),
                modulebook.getTitle(),
                modulebook.getStatus(),
                modulebook.getModulebookReleaseDate(),
                modulebook.getStudyExaminationRegulationsDate(),
                modulebook.getCreatedAt(),
                modulebook.getUpdatedAt()
        );
    }
}
