package de.bht.modulebook.modulebook;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.modulebook.dto.CreateModulebookRequest;
import de.bht.modulebook.modulebook.dto.ModulebookResponse;

@Service
public class ModulebookService {
    private final ModulebookRepository modulebookRepository;

    public ModulebookService(ModulebookRepository modulebookRepository) {
        this.modulebookRepository = modulebookRepository;
    }

    @Transactional
    public ModulebookResponse create(CreateModulebookRequest request) {
        String studyProgramName = normalizeStudyProgramName(request.requestedStudyProgramName());
        String degreeType = normalizeDegreeType(request.degreeType());

        modulebookRepository.findByStudyProgramNameAndDegreeType(studyProgramName, degreeType)
                .ifPresent(existing -> {
                    throw duplicateModulebook(studyProgramName, degreeType);
                });

        Modulebook modulebook = modulebookRepository.insert(studyProgramName, degreeType);
        return ModulebookMapper.toResponse(modulebook);
    }

    public Modulebook getById(Long id) {
        return modulebookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modulebook not found"));
    }

    public List<Modulebook> getAll() {
        return modulebookRepository.findAll();
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        boolean deleted = modulebookRepository.delete(id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Modulebook not found");
        }
    }

    private String normalizeStudyProgramName(String studyProgramName) {
        if (studyProgramName == null || studyProgramName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study program name is required");
        }
        return studyProgramName.trim();
    }

    private String normalizeDegreeType(String degreeType) {
        if (degreeType == null || degreeType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Degree type is required");
        }

        String normalized = degreeType.trim().toUpperCase();
        if (!normalized.equals("BACHELOR") && !normalized.equals("MASTER")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Degree type must be BACHELOR or MASTER");
        }
        return normalized;
    }

    private ResponseStatusException duplicateModulebook(String studyProgramName, String degreeType) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Modulebook " + studyProgramName + " " + degreeType + " already exists"
        );
    }
}
