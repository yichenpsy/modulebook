package de.bht.modulebook.modulebook;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.bht.modulebook.modulebook.dto.CreateModulebookRequest;
import de.bht.modulebook.modulebook.dto.ModulebookResponse;
import de.bht.modulebook.studyprogram.StudyProgram;
import de.bht.modulebook.studyprogram.StudyProgramService;

@Service
public class ModulebookService {
    private final ModulebookRepository modulebookRepository;
    private final StudyProgramService studyProgramService;

    public ModulebookService(
            ModulebookRepository modulebookRepository,
            StudyProgramService studyProgramService
    ) {
        this.modulebookRepository = modulebookRepository;
        this.studyProgramService = studyProgramService;
    }

    @Transactional
    public ModulebookResponse create(CreateModulebookRequest request) {
        StudyProgram studyProgram = new StudyProgram();
        studyProgram.setName(request.name());
        studyProgram.setDegreeType(request.degreeType());

        StudyProgram createdStudyProgram = studyProgramService.create(studyProgram);
        Modulebook modulebook = modulebookRepository.insertBlank(
                createdStudyProgram.getId(),
                titleFor(createdStudyProgram)
        );
        return ModulebookMapper.toResponse(modulebook, createdStudyProgram);
    }

    public Modulebook getOrCreateForStudyProgram(StudyProgram studyProgram) {
        return modulebookRepository.findByStudyProgramId(studyProgram.getId())
                .orElseGet(() -> modulebookRepository.insertBlank(studyProgram.getId(), titleFor(studyProgram)));
    }

    public Modulebook getOrCreateForStudyProgramId(Long studyProgramId) {
        StudyProgram studyProgram = studyProgramService.getById(studyProgramId);
        return getOrCreateForStudyProgram(studyProgram);
    }

    public ModulebookResponse getByStudyProgramId(Long studyProgramId) {
        StudyProgram studyProgram = studyProgramService.getById(studyProgramId);
        Modulebook modulebook = getOrCreateForStudyProgram(studyProgram);
        return ModulebookMapper.toResponse(modulebook, studyProgram);
    }

    private String titleFor(StudyProgram studyProgram) {
        return (studyProgram.getName() + " " + studyProgram.getDegreeType() + " Modulebook").trim();
    }
}
