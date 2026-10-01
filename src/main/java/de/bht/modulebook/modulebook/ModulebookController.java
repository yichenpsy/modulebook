package de.bht.modulebook.modulebook;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.dto.ModuleResponse;
import de.bht.modulebook.modulebook.dto.CreateModulebookRequest;
import de.bht.modulebook.modulebook.dto.ModulebookResponse;

@RestController
public class ModulebookController {
    private final ModulebookService service;
    private final ModuleService moduleService;

    public ModulebookController(ModulebookService service, ModuleService moduleService) {
        this.service = service;
        this.moduleService = moduleService;
    }

    @PostMapping("/api/modulebooks")
    @ResponseStatus(HttpStatus.CREATED)
    public ModulebookResponse create(@RequestBody CreateModulebookRequest request) {
        return service.create(request);
    }

    @GetMapping("/api/modulebooks")
    public List<ModulebookResponse> getAll() {
        return service.getAll()
                .stream()
                .map(ModulebookMapper::toResponse)
                .toList();
    }

    @GetMapping("/api/modulebooks/{id}")
    public ModulebookResponse getById(@PathVariable Long id) {
        return ModulebookMapper.toResponse(service.getById(id));
    }

    @DeleteMapping("/api/modulebooks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam String confirmation) {
        service.delete(id, confirmation);
    }

    @GetMapping("/api/modulebooks/{modulebookId}/modules")
    public List<ModuleResponse> getModules(@PathVariable Long modulebookId) {
        service.getById(modulebookId);
        return moduleService.getByModulebookId(modulebookId)
                .stream()
                .map(module -> moduleService.getModuleView(module.getId()))
                .toList();
    }
}
