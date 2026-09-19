package de.bht.modulebook.studyprogram;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.bht.modulebook.module.ModuleMapper;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.dto.ModuleResponse;
import de.bht.modulebook.studyprogram.dto.StudyProgramRequest;
import de.bht.modulebook.studyprogram.dto.StudyProgramResponse;

@RestController
@RequestMapping("/api/study-programs")
public class StudyProgramController {

    private final StudyProgramService studyProgramService;
    private final ModuleService moduleService;

    public StudyProgramController(
            StudyProgramService studyProgramService,
            ModuleService moduleService
    ) {
        this.studyProgramService = studyProgramService;
        this.moduleService = moduleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudyProgramResponse create(@RequestBody StudyProgramRequest request) {
        // Request values come from JSON sent by the frontend create/import forms.
        StudyProgram created = studyProgramService.create(StudyProgramMapper.toEntity(request));
        return StudyProgramMapper.toResponse(created);
    }

    @GetMapping
    public List<StudyProgramResponse> getAll() {
        // Used by the frontend to show the study-program selection list.
        return studyProgramService.getAll()
                .stream()
                .map(StudyProgramMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public StudyProgramResponse getById(@PathVariable Long id) {
        return StudyProgramMapper.toResponse(studyProgramService.getById(id));
    }

    @GetMapping("/{id}/modules")
    public List<ModuleResponse> getModules(@PathVariable Long id) {
        // First check the study program exists, so an invalid id returns 404.
        studyProgramService.getById(id);

        // Then load only modules whose modules.study_program_id equals this path id.
        return moduleService.getByStudyProgramId(id)
                .stream()
                .map(ModuleMapper::toResponse)
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        studyProgramService.delete(id);
    }
}
