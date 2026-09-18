package de.bht.modulebook.studyprogram;

import java.util.List;

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
        StudyProgram created = studyProgramRepository.insert(studyProgram);

        // Read it back so createdAt/updatedAt from the database are included.
        return getById(created.getId());
    }

    public StudyProgram findOrCreate(String name, Integer year) {
        // Import sends name/year, not id. So first look up the matching program.
        validateNameAndYear(name, year);
        StudyProgram studyProgram = studyProgramRepository.findByNameAndYear(name, year)
                // If it does not exist yet, create it once and reuse that id for modules.
                .orElseGet(() -> create(new StudyProgram(null, name, year, null, null)));
        studyProgramRepository.unhide(studyProgram.getId());
        return studyProgram;
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
        validateNameAndYear(studyProgram.getName(), studyProgram.getYear());
    }

    private void validateNameAndYear(String name, Integer year) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study program name is required");
        }
        if (year == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study program year is required");
        }
    }
}
