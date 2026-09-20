package de.bht.modulebook.studyprogram;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyProgramService {

    private final StudyProgramRepository studyProgramRepository;

    public StudyProgramService(StudyProgramRepository studyProgramRepository) {
        this.studyProgramRepository = studyProgramRepository;
    }

    public StudyProgram create(StudyProgram studyProgram) {
        // Validate before insert so bad request data becomes HTTP 400, not a database error.
        validate(studyProgram);
        rejectDuplicateNameAndDegreeType(studyProgram);

        StudyProgram created;
        try {
            created = studyProgramRepository.insert(studyProgram);
        } catch (DuplicateKeyException exception) {
            throw duplicateStudyProgram(studyProgram);
        }

        // Read it back so createdAt/updatedAt from the database are included.
        return getById(created.getId());
    }

    public StudyProgram getById(Long id) {
        return studyProgramRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study program not found"));
    }

    public List<StudyProgram> getAll() {
        return studyProgramRepository.findAll();
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        boolean deleted = studyProgramRepository.delete(id);

        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study program not found");
        }
    }

    private void validate(StudyProgram studyProgram) {
        if (studyProgram == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study program is required");
        }
        normalizeDegreeType(studyProgram);
        normalizeName(studyProgram);
        validateNameAndDegreeType(studyProgram.getName(), studyProgram.getDegreeType());
    }

    private void normalizeName(StudyProgram studyProgram) {
        if (studyProgram.getName() == null) {
            return;
        }

        studyProgram.setName(studyProgram.getName().trim());
    }

    private void normalizeDegreeType(StudyProgram studyProgram) {
        if (studyProgram.getDegreeType() == null) {
            return;
        }

        studyProgram.setDegreeType(studyProgram.getDegreeType().trim().toUpperCase());
    }

    private void validateNameAndDegreeType(String name, String degreeType) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study program name is required");
        }
        if (degreeType == null || degreeType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Degree type is required");
        }
        if (!degreeType.equals("BACHELOR") && !degreeType.equals("MASTER")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Degree type must be BACHELOR or MASTER");
        }
    }

    private void rejectDuplicateNameAndDegreeType(StudyProgram studyProgram) {
        if (studyProgramRepository.findByNameAndDegreeType(
                studyProgram.getName(),
                studyProgram.getDegreeType()
        ).isPresent()) {
            throw duplicateStudyProgram(studyProgram);
        }
    }

    private ResponseStatusException duplicateStudyProgram(StudyProgram studyProgram) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Study program " + studyProgram.getName() + " " + studyProgram.getDegreeType() + " already exists"
        );
    }
}
