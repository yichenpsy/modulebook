package de.bht.modulebook.module.version;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleMapper;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.dto.ModuleResponse;

@RestController
@RequestMapping("/api/modules/{moduleId}/versions")
public class ModuleVersionController {
    private final ModuleVersionService versionService;
    private final ModuleDiffService diffService;
    private final ModuleService moduleService;

    public ModuleVersionController(
            ModuleVersionService versionService,
            ModuleDiffService diffService,
            ModuleService moduleService
    ) {
        this.versionService = versionService;
        this.diffService = diffService;
        this.moduleService = moduleService;
    }

    @GetMapping
    public List<ModuleVersion> getHistory(@PathVariable Long moduleId) {
        return versionService.getHistory(moduleId);
    }

    @GetMapping("/compare")
    public ModuleDiff compare(
            @PathVariable Long moduleId,
            @RequestParam int from,
            @RequestParam int to
    ) {
        return diffService.compare(moduleId, from, to);
    }

    @PostMapping("/{versionNumber}/restore")
    public ModuleResponse restore(@PathVariable Long moduleId, @PathVariable int versionNumber) {
        Module restored = versionService.restore(moduleId, versionNumber);
        return ModuleMapper.toResponse(
                restored,
                moduleService.getWorkloadByModuleId(moduleId).orElse(null),
                moduleService.getTeachingFormsByModuleId(moduleId)
        );
    }
}
