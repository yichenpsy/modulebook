package de.bht.modulebook.modulebook;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.bht.modulebook.modulebook.dto.ModulebookVersionRequest;
import de.bht.modulebook.modulebook.dto.ModulebookVersionResponse;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSet;

@RestController
public class ModulebookVersionController {
    private final ModulebookVersionService service;

    public ModulebookVersionController(ModulebookVersionService service) {
        this.service = service;
    }

    @PostMapping("/api/study-programs/{studyProgramId}/modulebook-versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ModulebookVersionResponse create(
            @PathVariable Long studyProgramId,
            @RequestBody ModulebookVersionRequest request
    ) {
        return ModulebookVersionMapper.toResponse(
                service.create(
                        studyProgramId,
                        request.versionMessage(),
                        request.modulebookReleaseDate(),
                        request.studyExaminationRegulationsDate()
                )
        );
    }

    @GetMapping("/api/study-programs/{studyProgramId}/modulebook-versions")
    public List<ModulebookVersionResponse> getByStudyProgramId(@PathVariable Long studyProgramId) {
        return service.getByStudyProgramId(studyProgramId)
                .stream()
                .map(ModulebookVersionMapper::toResponse)
                .toList();
    }

    @GetMapping("/api/modulebook-versions/{versionId}")
    public ModulebookVersionResponse getById(@PathVariable Long versionId) {
        return ModulebookVersionMapper.toResponse(service.getById(versionId));
    }

    @DeleteMapping("/api/modulebook-versions/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long versionId) {
        service.delete(versionId);
    }

    @PostMapping("/api/modulebook-versions/{versionId}/restore")
    public StudyProgramChangeSet restore(@PathVariable Long versionId) {
        return service.restore(versionId);
    }
}
