package de.bht.modulebook.module;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModuleService {

    private final ModuleRepository moduleRepository;

    public ModuleService(ModuleRepository moduleRepository) {
        // The service needs the repository to access the database.
        this.moduleRepository = moduleRepository;
    }
    
    public Module create(Module module) {
        Module created = moduleRepository.insert(module);
        return getById(created.getId());
    }

    public Module getById(Long id) {
        return moduleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
    }

    public List<Module> getAll() {
        return moduleRepository.findAll();
    }

    public Module update(Long id, Module module) {
        boolean updated = moduleRepository.update(id, module);

        // The repository returns false when no row with this id exists.
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }
        return getById(id);
    }

    public void delete(Long id) {
        boolean deleted = moduleRepository.delete(id);

        // The repository returns false when no row with this id exists.
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }
    }
}
