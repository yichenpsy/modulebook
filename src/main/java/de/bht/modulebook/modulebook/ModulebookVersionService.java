package de.bht.modulebook.modulebook;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleMapper;
import de.bht.modulebook.module.ModuleRepository;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.dto.ModuleResponse;
import de.bht.modulebook.module.teachingform.ModuleTeachingFormRepository;
import de.bht.modulebook.module.version.ModuleChangeLogService;
import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.module.workload.ModuleWorkloadRepository;
import de.bht.modulebook.module.workload.PresenceHourComponent;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSet;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;
import de.bht.modulebook.studyprogram.dto.StudyProgramResponse;
import de.bht.modulebook.teachingform.TeachingForm;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ModulebookVersionService {
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_INITIAL_IMPORT = "INITIAL_IMPORT";
    public static final String INITIAL_IMPORT_VERSION_MESSAGE = "Version 1";

    private final ModulebookVersionRepository repository;
    private final ModulebookService modulebookService;
    private final ModuleService moduleService;
    private final ModuleRepository moduleRepository;
    private final ModuleWorkloadRepository workloadRepository;
    private final ModuleTeachingFormRepository teachingFormRepository;
    private final StudyProgramChangeSetService changeSetService;
    private final ModuleChangeLogService changeLogService;
    private final JsonMapper jsonMapper;

    public ModulebookVersionService(
            ModulebookVersionRepository repository,
            ModulebookService modulebookService,
            ModuleService moduleService,
            ModuleRepository moduleRepository,
            ModuleWorkloadRepository workloadRepository,
            ModuleTeachingFormRepository teachingFormRepository,
            StudyProgramChangeSetService changeSetService,
            ModuleChangeLogService changeLogService,
            JsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.modulebookService = modulebookService;
        this.moduleService = moduleService;
        this.moduleRepository = moduleRepository;
        this.workloadRepository = workloadRepository;
        this.teachingFormRepository = teachingFormRepository;
        this.changeSetService = changeSetService;
        this.changeLogService = changeLogService;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public ModulebookVersion create(
            Long studyProgramId,
            String versionMessage,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate
    ) {
        String normalizedMessage = normalizeVersionMessage(versionMessage);
        Modulebook modulebook = modulebookService.getById(studyProgramId);
        ModulebookSnapshot snapshot = buildSnapshot(modulebook);

        try {
            String snapshotJson = jsonMapper.writeValueAsString(snapshot);
            return repository.insert(
                    modulebook,
                    normalizedMessage,
                    snapshotJson,
                    SOURCE_MANUAL,
                    null,
                    modulebookReleaseDate,
                    studyExaminationRegulationsDate
            );
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create modulebook version");
        }
    }

    @Transactional
    public ModulebookVersion createInitialImportVersion(
            Long studyProgramId,
            Long sourceChangeSetId,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate
    ) {
        Modulebook modulebook = modulebookService.getById(studyProgramId);
        ModulebookSnapshot snapshot = buildSnapshot(modulebook);

        try {
            String snapshotJson = jsonMapper.writeValueAsString(snapshot);
            return repository.insert(
                    modulebook,
                    INITIAL_IMPORT_VERSION_MESSAGE,
                    snapshotJson,
                    SOURCE_INITIAL_IMPORT,
                    sourceChangeSetId,
                    modulebookReleaseDate,
                    studyExaminationRegulationsDate
            );
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create initial import version");
        }
    }

    public List<ModulebookVersion> getByStudyProgramId(Long studyProgramId) {
        modulebookService.getById(studyProgramId);
        return repository.findByStudyProgramId(studyProgramId);
    }

    public ModulebookVersion getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modulebook version not found"));
    }

    public void delete(Long id) {
        ModulebookVersion version = getById(id);
        if (SOURCE_INITIAL_IMPORT.equals(version.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Initial import version cannot be deleted");
        }

        boolean deleted = repository.delete(id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Modulebook version not found");
        }
    }

    @Transactional
    public StudyProgramChangeSet restore(Long id) {
        ModulebookVersion version = getById(id);
        ModulebookSnapshot snapshot = parseSnapshot(version);
        StudyProgramChangeSet changeSet = changeSetService.create(
                version.getStudyProgramId(),
                StudyProgramChangeSetService.SOURCE_RESTORE_TAG,
                "back to modulebook version " + version.getId()
        );
        Set<Long> restoredModuleIds = new HashSet<>();

        for (ModuleResponse moduleResponse : snapshot.modules()) {
            Module desiredModule = moduleFromResponse(moduleResponse, version.getStudyProgramId());
            ModuleWorkload desiredWorkload = workloadFromResponse(moduleResponse);
            List<Long> desiredTeachingFormIds = teachingFormIdsFromResponse(moduleResponse);
            Module existingModule = findExistingModule(moduleResponse, version.getStudyProgramId());

            if (existingModule == null) {
                desiredModule.setId(null);
                desiredModule.setCreatedChangeSetId(changeSet.getId());
                Module created = moduleRepository.insert(desiredModule);
                if (desiredWorkload != null) {
                    workloadRepository.upsert(created.getId(), desiredWorkload);
                }
                teachingFormRepository.replaceForModule(created.getId(), desiredTeachingFormIds);
                restoredModuleIds.add(created.getId());
                continue;
            }

            restoredModuleIds.add(existingModule.getId());
            updateModuleToTagState(existingModule, desiredModule, desiredWorkload, desiredTeachingFormIds, changeSet.getId());
        }

        markMissingActiveModulesDeleted(version.getStudyProgramId(), restoredModuleIds, changeSet.getId());
        return changeSet;
    }

    private ModulebookSnapshot parseSnapshot(ModulebookVersion version) {
        try {
            return jsonMapper.readValue(version.getSnapshotJson(), ModulebookSnapshot.class);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not parse modulebook version");
        }
    }

    private Module findExistingModule(ModuleResponse moduleResponse, Long studyProgramId) {
        if (moduleResponse.id() != null) {
            Module byId = moduleRepository.findById(moduleResponse.id()).orElse(null);
            if (byId != null && Objects.equals(byId.getStudyProgramId(), studyProgramId)) {
                return byId;
            }
        }

        if (moduleResponse.moduleNumber() == null || moduleResponse.moduleNumber().isBlank()) {
            return null;
        }

        return moduleRepository
                .findByStudyProgramIdAndModuleNumber(studyProgramId, moduleResponse.moduleNumber())
                .orElse(null);
    }

    private void updateModuleToTagState(
            Module existingModule,
            Module desiredModule,
            ModuleWorkload desiredWorkload,
            List<Long> desiredTeachingFormIds,
            Long changeSetId
    ) {
        Module oldModule = copyModule(existingModule);
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(existingModule.getId()).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(existingModule.getId());

        moduleRepository.update(existingModule.getId(), desiredModule);
        if (desiredWorkload == null) {
            workloadRepository.deleteByModuleId(existingModule.getId());
        } else {
            workloadRepository.upsert(existingModule.getId(), desiredWorkload);
        }
        teachingFormRepository.replaceForModule(existingModule.getId(), desiredTeachingFormIds);

        Module savedModule = moduleRepository.findById(existingModule.getId()).orElseThrow();
        ModuleWorkload savedWorkload = workloadRepository.findByModuleId(existingModule.getId()).orElse(null);
        List<Long> savedTeachingFormIds = teachingFormIdsByModuleId(existingModule.getId());
        changeLogService.saveChanges(
                changeSetId,
                existingModule.getId(),
                oldModule,
                savedModule,
                oldWorkload,
                savedWorkload,
                oldTeachingFormIds,
                savedTeachingFormIds
        );
    }

    private void markMissingActiveModulesDeleted(Long studyProgramId, Set<Long> restoredModuleIds, Long changeSetId) {
        for (Module module : moduleRepository.findByStudyProgramId(studyProgramId)) {
            if (restoredModuleIds.contains(module.getId()) || !isActiveModule(module)) {
                continue;
            }

            Module desiredModule = copyModule(module);
            desiredModule.setModuleStatus(ModuleService.DEACTIVE_STATUS);
            updateModuleToTagState(
                    module,
                    desiredModule,
                    workloadRepository.findByModuleId(module.getId()).orElse(null),
                    teachingFormIdsByModuleId(module.getId()),
                    changeSetId
            );
        }
    }

    private Module moduleFromResponse(ModuleResponse response, Long studyProgramId) {
        Long modulebookId = response.modulebookId() == null ? studyProgramId : response.modulebookId();
        return new Module(
                response.id(),
                studyProgramId,
                response.moduleNumber(),
                response.name(),
                response.credits(),
                response.description(),
                modulebookId,
                response.moduleType(),
                response.titleDe(),
                response.titleEn(),
                response.coordinator(),
                response.applicability(),
                response.learningArea(),
                response.learningObjectives(),
                response.prerequisites(),
                response.level(),
                response.moduleStatus(),
                response.moduleComments(),
                response.frequency(),
                response.examinationForm(),
                response.contents(),
                response.literature(),
                response.additionalNotes(),
                response.moduleWeight(),
                response.createdAt(),
                response.updatedAt()
        );
    }

    private ModuleWorkload workloadFromResponse(ModuleResponse response) {
        if (response.workload() == null) {
            return null;
        }

        ModuleWorkload workload = new ModuleWorkload();
        workload.setPresenceHours(response.workload().presenceHours());
        workload.setSelfStudyHours(response.workload().selfStudyHours());
        workload.setRawText(response.workload().rawText());
        workload.setComponents(
                response.workload().components() == null
                        ? List.of()
                        : response.workload().components().stream()
                                .map(componentResponse -> {
                                    PresenceHourComponent component = new PresenceHourComponent();
                                    component.setTeachingFormId(componentResponse.teachingFormId());
                                    component.setSwsAmount(componentResponse.swsAmount());
                                    return component;
                                })
                                .toList()
        );
        return workload;
    }

    private List<Long> teachingFormIdsFromResponse(ModuleResponse response) {
        if (response.teachingForms() == null) {
            return List.of();
        }

        return response.teachingForms().stream()
                .map(teachingForm -> teachingForm.id())
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Long> teachingFormIdsByModuleId(Long moduleId) {
        return teachingFormRepository.findByModuleId(moduleId)
                .stream()
                .map(TeachingForm::getId)
                .toList();
    }

    private Module copyModule(Module source) {
        return new Module(
                source.getId(),
                source.getStudyProgramId(),
                source.getModuleNumber(),
                source.getName(),
                source.getCredits(),
                source.getDescription(),
                source.getModulebookId(),
                source.getModuleType(),
                source.getTitleDe(),
                source.getTitleEn(),
                source.getCoordinator(),
                source.getApplicability(),
                source.getLearningArea(),
                source.getLearningObjectives(),
                source.getPrerequisites(),
                source.getLevel(),
                source.getModuleStatus(),
                source.getModuleComments(),
                source.getFrequency(),
                source.getExaminationForm(),
                source.getContents(),
                source.getLiterature(),
                source.getAdditionalNotes(),
                source.getModuleWeight(),
                source.getCreatedAt(),
                source.getUpdatedAt(),
                source.getCreatedChangeSetId()
        );
    }

    private ModulebookSnapshot buildSnapshot(Modulebook modulebook) {
        var modules = moduleService.getByStudyProgramId(modulebook.getId())
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
                new StudyProgramResponse(
                        modulebook.getId(),
                        modulebook.getStudyProgramName(),
                        modulebook.getDegreeType(),
                        modulebook.getCreatedAt()
                ),
                modules
        );
    }

    private boolean isActiveModule(Module module) {
        return ModuleService.ACTIVE_STATUS.equalsIgnoreCase(String.valueOf(module.getModuleStatus()));
    }

    private String normalizeVersionMessage(String versionMessage) {
        if (versionMessage == null || versionMessage.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version message is required");
        }

        return versionMessage.trim();
    }
}
