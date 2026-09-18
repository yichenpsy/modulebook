package de.bht.modulebook.studyprogram.changeset;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.bht.modulebook.module.version.ModuleFieldChange;
import de.bht.modulebook.module.version.ModuleFieldChangeRepository;
import de.bht.modulebook.studyprogram.StudyProgramService;

@RestController
public class StudyProgramChangeSetController {
    private final StudyProgramChangeSetService changeSetService;
    private final StudyProgramService studyProgramService;
    private final ModuleFieldChangeRepository fieldChangeRepository;
    private final ChangeSetRestoreService restoreService;

    public StudyProgramChangeSetController(
            StudyProgramChangeSetService changeSetService,
            StudyProgramService studyProgramService,
            ModuleFieldChangeRepository fieldChangeRepository,
            ChangeSetRestoreService restoreService
    ) {
        this.changeSetService = changeSetService;
        this.studyProgramService = studyProgramService;
        this.fieldChangeRepository = fieldChangeRepository;
        this.restoreService = restoreService;
    }

    @GetMapping("/api/study-programs/{studyProgramId}/change-sets")
    public List<StudyProgramChangeSet> getChangeSets(@PathVariable Long studyProgramId) {
        studyProgramService.getById(studyProgramId);
        return changeSetService.getByStudyProgramId(studyProgramId);
    }

    @GetMapping("/api/change-sets/{changeSetId}/field-changes")
    public List<ModuleFieldChange> getFieldChanges(@PathVariable Long changeSetId) {
        return fieldChangeRepository.findByChangeSetId(changeSetId);
    }

    @GetMapping("/api/modules/{moduleId}/field-changes")
    public List<ModuleFieldChange> getModuleFieldChanges(@PathVariable Long moduleId) {
        return fieldChangeRepository.findByModuleId(moduleId);
    }

    @PostMapping("/api/change-sets/{changeSetId}/restore-before")
    public StudyProgramChangeSet restoreBefore(
            @PathVariable Long changeSetId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return restoreService.restoreBefore(changeSetId, force);
    }

    @PostMapping("/api/change-sets/{changeSetId}/reset-to-initial-import")
    public StudyProgramChangeSet resetToInitialImport(@PathVariable Long changeSetId) {
        return restoreService.resetToInitialImport(changeSetId);
    }
}
