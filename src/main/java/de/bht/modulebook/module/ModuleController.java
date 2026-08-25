package de.bht.modulebook.module;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.module.dto.ModuleRequest;
import de.bht.modulebook.module.dto.ModuleResponse;
import jakarta.validation.Valid;

import de.bht.modulebook.module.workload.ModuleWorkloadMapper;

// HTTP API layer, defines endpoints
@RestController // this class handles web requests and returns data
@RequestMapping("/api/modules")  // every endpoint in this controller starts with /api/modules
public class ModuleController {
    private final ModuleService moduleService;

    public ModuleController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @PostMapping // handles POST /api/modules
    @ResponseStatus(HttpStatus.CREATED) //makes the API return HTTP 201 Created.
    public ModuleResponse create(@Valid @RequestBody ModuleRequest request) {
        Module created = moduleService.create(
            ModuleMapper.toEntity(request),
            ModuleWorkloadMapper.toEntity(request.workload()),
            request.teachingFormIds()
        );
        return ModuleMapper.toResponse(
            created,
            moduleService.getWorkloadByModuleId(created.getId()).orElse(null),
            moduleService.getTeachingFormsByModuleId(created.getId())
        );
    }

    @GetMapping //GET /api/modules
    public List<ModuleResponse> getAll() {
        return moduleService.getAll()
            .stream()
            .map(ModuleMapper::toResponse)
            .toList();
    }

    @GetMapping("/{id}") //GET /api/modules/1
    public ModuleResponse getById(@PathVariable Long id) {
        Module module = moduleService.getById(id);
        return ModuleMapper.toResponse(
            module,
            moduleService.getWorkloadByModuleId(id).orElse(null),
            moduleService.getTeachingFormsByModuleId(id)
        );
    }

    @PutMapping("/{id}") //PUT /api/modules/1
    public ModuleResponse update(@PathVariable Long id, @Valid @RequestBody ModuleRequest request) {
        if (request.versionReason() == null || request.versionReason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commit message is required");
        }

        Module updated = moduleService.update(
            id, 
            ModuleMapper.toEntity(request),
            ModuleWorkloadMapper.toEntity(request.workload()),
            request.teachingFormIds(),
            request.versionReason()
        );
        return ModuleMapper.toResponse(
            updated,
            moduleService.getWorkloadByModuleId(id).orElse(null),
            moduleService.getTeachingFormsByModuleId(id)
        );
    }

    @DeleteMapping("/{id}") // DELETE /api/modules/1
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        moduleService.delete(id);
    }
}
