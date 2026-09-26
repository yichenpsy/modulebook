package de.bht.modulebook.modulebook.changeset;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.bht.modulebook.module.version.ModuleChange;
import de.bht.modulebook.module.version.ModuleChangeRepository;
import de.bht.modulebook.modulebook.ModulebookService;

@RestController
public class ModulebookChangeSetController {
    private final ModulebookChangeSetService changeSetService;
    private final ModulebookService modulebookService;
    private final ModuleChangeRepository changeRepository;
    private final ChangeSetRestoreService restoreService;

    public ModulebookChangeSetController(
            ModulebookChangeSetService changeSetService,
            ModulebookService modulebookService,
            ModuleChangeRepository changeRepository,
            ChangeSetRestoreService restoreService
    ) {
        this.changeSetService = changeSetService;
        this.modulebookService = modulebookService;
        this.changeRepository = changeRepository;
        this.restoreService = restoreService;
    }

    @GetMapping("/api/modulebooks/{modulebookId}/change-sets")
    public List<ModulebookChangeSet> getChangeSets(@PathVariable Long modulebookId) {
        modulebookService.getById(modulebookId);
        return changeSetService.getByModulebookId(modulebookId);
    }

    @GetMapping({
            "/api/change-sets/{changeSetId}/module-changes",
            "/api/change-sets/{changeSetId}/field-changes"
    })
    public List<ModuleChange> getModuleChanges(@PathVariable Long changeSetId) {
        return changeRepository.findByChangeSetId(changeSetId);
    }

    @GetMapping({
            "/api/modules/{moduleId}/module-changes",
            "/api/modules/{moduleId}/field-changes"
    })
    public List<ModuleChange> getModuleChangesByModule(@PathVariable Long moduleId) {
        return changeRepository.findByModuleId(moduleId);
    }

    @PostMapping("/api/change-sets/{changeSetId}/restore-before")
    public ModulebookChangeSet restoreBefore(
            @PathVariable Long changeSetId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return restoreService.restoreBefore(changeSetId, force);
    }

    @PostMapping("/api/change-sets/{changeSetId}/reset-to-initial-import")
    public ModulebookChangeSet resetToInitialImport(@PathVariable Long changeSetId) {
        return restoreService.resetToInitialImport(changeSetId);
    }
}
