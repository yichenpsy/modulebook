package de.bht.modulebook.modulebook;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.bht.modulebook.modulebook.dto.ModulebookTagRequest;
import de.bht.modulebook.modulebook.dto.ModulebookTagResponse;

@RestController
public class ModulebookTagController {
    private final ModulebookTagService service;

    public ModulebookTagController(ModulebookTagService service) {
        this.service = service;
    }

    @PostMapping("/api/study-programs/{studyProgramId}/modulebook-tags")
    @ResponseStatus(HttpStatus.CREATED)
    public ModulebookTagResponse create(
            @PathVariable Long studyProgramId,
            @RequestBody ModulebookTagRequest request
    ) {
        return ModulebookTagMapper.toResponse(
                service.create(studyProgramId, request.tagMessage())
        );
    }

    @GetMapping("/api/study-programs/{studyProgramId}/modulebook-tags")
    public List<ModulebookTagResponse> getByStudyProgramId(@PathVariable Long studyProgramId) {
        return service.getByStudyProgramId(studyProgramId)
                .stream()
                .map(ModulebookTagMapper::toResponse)
                .toList();
    }

    @GetMapping("/api/modulebook-tags/{tagId}")
    public ModulebookTagResponse getById(@PathVariable Long tagId) {
        return ModulebookTagMapper.toResponse(service.getById(tagId));
    }
}

