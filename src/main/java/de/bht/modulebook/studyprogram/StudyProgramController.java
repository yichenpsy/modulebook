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
import de.bht.modulebook.modulebook.ModulebookService;
import de.bht.modulebook.modulebook.dto.CreateModulebookRequest;
import de.bht.modulebook.modulebook.dto.ModulebookResponse;
import de.bht.modulebook.studyprogram.dto.StudyProgramRequest;
import de.bht.modulebook.studyprogram.dto.StudyProgramResponse;

@RestController
@RequestMapping("/api/study-programs")
public class StudyProgramController {

    private final ModulebookService modulebookService;
    private final ModuleService moduleService;

    public StudyProgramController(
            ModulebookService modulebookService,
            ModuleService moduleService
    ) {
        this.modulebookService = modulebookService;
        this.moduleService = moduleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudyProgramResponse create(@RequestBody StudyProgramRequest request) {
        ModulebookResponse created = modulebookService.create(new CreateModulebookRequest(request.name(), request.degreeType()));
        return new StudyProgramResponse(
                created.id(),
                created.studyProgramName(),
                created.degreeType(),
                created.createdAt()
        );
    }

    @GetMapping
    public List<StudyProgramResponse> getAll() {
        // Kept for frontend compatibility. The ids are now modulebooks.id values.
        return modulebookService.getAll()
                .stream()
                .map(StudyProgramMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public StudyProgramResponse getById(@PathVariable Long id) {
        return StudyProgramMapper.toResponse(modulebookService.getById(id));
    }

    @GetMapping("/{id}/modules")
    public List<ModuleResponse> getModules(@PathVariable Long id) {
        // First check the modulebook exists, so an invalid id returns 404.
        modulebookService.getById(id);

        // Then load only modules whose modules.modulebook_id equals this path id.
        return moduleService.getByStudyProgramId(id)
                .stream()
                .map(ModuleMapper::toResponse)
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        modulebookService.delete(id);
    }
}
