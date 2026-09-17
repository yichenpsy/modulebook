package de.bht.modulebook.modulebook;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleMapper;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.studyprogram.StudyProgram;
import de.bht.modulebook.studyprogram.StudyProgramMapper;
import de.bht.modulebook.studyprogram.StudyProgramService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ModulebookTagService {
    private final ModulebookTagRepository repository;
    private final StudyProgramService studyProgramService;
    private final ModuleService moduleService;
    private final JsonMapper jsonMapper;

    public ModulebookTagService(
            ModulebookTagRepository repository,
            StudyProgramService studyProgramService,
            ModuleService moduleService,
            JsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.studyProgramService = studyProgramService;
        this.moduleService = moduleService;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public ModulebookTag create(Long studyProgramId, String tagMessage) {
        String normalizedMessage = normalizeTagMessage(tagMessage);
        StudyProgram studyProgram = studyProgramService.getById(studyProgramId);
        ModulebookSnapshot snapshot = buildSnapshot(studyProgram);

        try {
            String snapshotJson = jsonMapper.writeValueAsString(snapshot);
            return repository.insert(studyProgramId, normalizedMessage, snapshotJson);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create modulebook tag");
        }
    }

    public List<ModulebookTag> getByStudyProgramId(Long studyProgramId) {
        studyProgramService.getById(studyProgramId);
        return repository.findByStudyProgramId(studyProgramId);
    }

    public ModulebookTag getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modulebook tag not found"));
    }

    private ModulebookSnapshot buildSnapshot(StudyProgram studyProgram) {
        var modules = moduleService.getByStudyProgramId(studyProgram.getId())
                .stream()
                .filter(this::isActiveModule)
                .sorted(Comparator.comparing(
                        module -> String.valueOf(module.getModuleNumber()),
                        String.CASE_INSENSITIVE_ORDER
                ))
                .map(module -> ModuleMapper.toResponse(
                        module,
                        moduleService.getWorkloadByModuleId(module.getId()).orElse(null),
                        moduleService.getTeachingFormsByModuleId(module.getId())
                ))
                .toList();

        return new ModulebookSnapshot(
                StudyProgramMapper.toResponse(studyProgram),
                modules
        );
    }

    private boolean isActiveModule(Module module) {
        return ModuleService.ACTIVE_STATUS.equalsIgnoreCase(String.valueOf(module.getModuleStatus()));
    }

    private String normalizeTagMessage(String tagMessage) {
        if (tagMessage == null || tagMessage.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag message is required");
        }

        return tagMessage.trim();
    }
}

