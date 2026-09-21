package de.bht.modulebook.modulebook;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.bht.modulebook.modulebook.dto.CreateModulebookRequest;
import de.bht.modulebook.modulebook.dto.ModulebookResponse;

@RestController
public class ModulebookController {
    private final ModulebookService service;

    public ModulebookController(ModulebookService service) {
        this.service = service;
    }

    @PostMapping("/api/modulebooks")
    @ResponseStatus(HttpStatus.CREATED)
    public ModulebookResponse create(@RequestBody CreateModulebookRequest request) {
        return service.create(request);
    }

    @GetMapping("/api/study-programs/{studyProgramId}/modulebook")
    public ModulebookResponse getByStudyProgramId(@PathVariable Long studyProgramId) {
        return service.getByStudyProgramId(studyProgramId);
    }
}
