package de.bht.modulebook.module.version;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleMapper;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.dto.ModuleResponse;

@RestController
public class DeletedModuleVersionController {
    private final ModuleVersionService versionService;
    private final ModuleService moduleService;

    public DeletedModuleVersionController(ModuleVersionService versionService, ModuleService moduleService) {
        this.versionService = versionService;
        this.moduleService = moduleService;
    }

    @PostMapping("/api/module-versions/{versionId}/restore-deleted")
    public ModuleResponse restoreDeleted(@PathVariable Long versionId) {
        Module restored = versionService.restoreDeleted(versionId);
        return ModuleMapper.toResponse(
                restored,
                moduleService.getWorkloadByModuleId(restored.getId()).orElse(null),
                moduleService.getTeachingFormsByModuleId(restored.getId())
        );
    }
}
