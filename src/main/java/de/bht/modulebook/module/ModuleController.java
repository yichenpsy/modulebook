package de.bht.modulebook.module;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import de.bht.modulebook.module.dto.ModuleRequest;
import de.bht.modulebook.module.dto.ModuleResponse;

@RestController // this class handles web requests and returns data
@RequestMapping("/api/modules")  // every endpoint in this controller starts with /api/modules
public class ModuleController {
    private final ModuleService moduleService;

    public ModuleController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @PostMapping // handles POST /api/modules
    @ResponseStatus(HttpStatus.CREATED) //makes the API return HTTP 201 Created.
    public ModuleResponse create(@RequestBody ModuleRequest request) {
        Module created = moduleService.create(ModuleMapper.toEntity(request));
        return ModuleMapper.toResponse(created);
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
        return ModuleMapper.toResponse(moduleService.getById(id));
    }

    @PutMapping("/{id}") //PUT /api/modules/1
    public ModuleResponse update(@PathVariable Long id, @RequestBody ModuleRequest request) {
        Module updated = moduleService.update(id, ModuleMapper.toEntity(request));
        return ModuleMapper.toResponse(updated);
    }

    @DeleteMapping("/{id}") // DELETE /api/modules/1
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        moduleService.delete(id);
    }
}
