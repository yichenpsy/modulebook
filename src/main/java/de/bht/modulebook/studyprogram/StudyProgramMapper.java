package de.bht.modulebook.studyprogram;

import de.bht.modulebook.studyprogram.dto.StudyProgramRequest;
import de.bht.modulebook.studyprogram.dto.StudyProgramResponse;

public class StudyProgramMapper {

    // Converts frontend JSON into the internal object used by service/repository code.
    public static StudyProgram toEntity(StudyProgramRequest request) {
        StudyProgram studyProgram = new StudyProgram();
        studyProgram.setName(request.name());
        studyProgram.setDegreeType(request.degreeType());
        return studyProgram;
    }

    // Converts the database object into the API response shape.
    public static StudyProgramResponse toResponse(StudyProgram studyProgram) {
        return new StudyProgramResponse(
                studyProgram.getId(),
                studyProgram.getName(),
                studyProgram.getDegreeType(),
                studyProgram.getCreatedAt(),
                studyProgram.getUpdatedAt()
        );
    }
}
