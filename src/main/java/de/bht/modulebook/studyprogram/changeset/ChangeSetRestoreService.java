package de.bht.modulebook.studyprogram.changeset;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleRepository;
import de.bht.modulebook.module.version.ModuleChangeLogService;
import de.bht.modulebook.module.version.ModuleFieldChange;
import de.bht.modulebook.module.version.ModuleFieldChangeRepository;
import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.module.workload.ModuleWorkloadRepository;
import de.bht.modulebook.module.teachingform.ModuleTeachingFormRepository;
import de.bht.modulebook.studyprogram.StudyProgramRepository;
import de.bht.modulebook.teachingform.TeachingForm;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ChangeSetRestoreService {
    private final StudyProgramChangeSetRepository changeSetRepository;
    private final StudyProgramChangeSetService changeSetService;
    private final ModuleFieldChangeRepository fieldChangeRepository;
    private final ModuleRepository moduleRepository;
    private final ModuleWorkloadRepository workloadRepository;
    private final ModuleTeachingFormRepository teachingFormRepository;
    private final ModuleChangeLogService changeLogService;
    private final StudyProgramRepository studyProgramRepository;
    private final JsonMapper jsonMapper;

    public ChangeSetRestoreService(
            StudyProgramChangeSetRepository changeSetRepository,
            StudyProgramChangeSetService changeSetService,
            ModuleFieldChangeRepository fieldChangeRepository,
            ModuleRepository moduleRepository,
            ModuleWorkloadRepository workloadRepository,
            ModuleTeachingFormRepository teachingFormRepository,
            ModuleChangeLogService changeLogService,
            StudyProgramRepository studyProgramRepository,
            JsonMapper jsonMapper
    ) {
        this.changeSetRepository = changeSetRepository;
        this.changeSetService = changeSetService;
        this.fieldChangeRepository = fieldChangeRepository;
        this.moduleRepository = moduleRepository;
        this.workloadRepository = workloadRepository;
        this.teachingFormRepository = teachingFormRepository;
        this.changeLogService = changeLogService;
        this.studyProgramRepository = studyProgramRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public StudyProgramChangeSet restoreBefore(Long changeSetId, boolean force) {
        StudyProgramChangeSet target = changeSetRepository.findById(changeSetId);
        validateTarget(target);

        List<StudyProgramChangeSet> restoreCollection = changeSetRepository.findActiveFromInclusive(target);
        List<Long> restoreChangeSetIds = restoreCollection.stream()
                .map(StudyProgramChangeSet::getId)
                .toList();
        List<Module> modulesToDelete = moduleRepository.findByCreatedChangeSetIds(restoreChangeSetIds);

        if (!force && !modulesToDelete.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Restore would delete created modules: " + moduleLabels(modulesToDelete)
            );
        }

        StudyProgramChangeSet restoreChangeSet = changeSetService.create(
                target.getStudyProgramId(),
                StudyProgramChangeSetService.SOURCE_RESTORE_BEFORE,
                "restore to before change set " + target.getId()
        );

        Set<Long> moduleIdsToDelete = modulesToDelete.stream()
                .map(Module::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        restoreFields(restoreChangeSetIds, moduleIdsToDelete, restoreChangeSet.getId());
        deleteCreatedModules(modulesToDelete);
        changeSetRepository.markDiscarded(restoreChangeSetIds, restoreChangeSet.getId());
        hideStudyProgramIfImportWasFullyRestored(target.getStudyProgramId(), restoreCollection, restoreChangeSet.getId());

        return restoreChangeSet;
    }

    @Transactional
    public StudyProgramChangeSet resetToInitialImport(Long changeSetId) {
        StudyProgramChangeSet target = changeSetRepository.findById(changeSetId);
        validateInitialImportTarget(target);

        List<StudyProgramChangeSet> laterChangeSets = changeSetRepository.findAfter(target);
        List<Long> laterChangeSetIds = laterChangeSets.stream()
                .map(StudyProgramChangeSet::getId)
                .toList();
        List<Module> modulesToDelete = moduleRepository.findByCreatedChangeSetIds(laterChangeSetIds);
        Set<Long> moduleIdsToDelete = modulesToDelete.stream()
                .map(Module::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        restoreFields(laterChangeSetIds, moduleIdsToDelete, null);
        deleteCreatedModules(modulesToDelete);
        changeSetRepository.deleteByIds(laterChangeSetIds);
        studyProgramRepository.unhide(target.getStudyProgramId());

        return target;
    }

    private void validateInitialImportTarget(StudyProgramChangeSet target) {
        if (!StudyProgramChangeSetService.SOURCE_PDF_IMPORT.equals(target.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only the initial PDF import can clean history");
        }

        StudyProgramChangeSet initialImport = changeSetRepository
                .findFirstByStudyProgramIdAndSourceType(
                        target.getStudyProgramId(),
                        StudyProgramChangeSetService.SOURCE_PDF_IMPORT
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Initial PDF import not found"));
        if (!Objects.equals(initialImport.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only the first PDF import can clean history");
        }
    }

    private void hideStudyProgramIfImportWasFullyRestored(
            Long studyProgramId,
            List<StudyProgramChangeSet> restoreCollection,
            Long restoreChangeSetId
    ) {
        boolean restoresPdfImport = restoreCollection.stream()
                .anyMatch(changeSet -> StudyProgramChangeSetService.SOURCE_PDF_IMPORT.equals(changeSet.getSourceType()));
        if (restoresPdfImport) {
            studyProgramRepository.hideIfNoModules(studyProgramId, restoreChangeSetId);
        }
    }

    private void validateTarget(StudyProgramChangeSet target) {
        if (target.getDiscardedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discarded change sets cannot be restored");
        }
        if (StudyProgramChangeSetService.SOURCE_DELETE.equals(target.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delete change sets cannot be restored");
        }
        if (StudyProgramChangeSetService.SOURCE_PDF_IMPORT.equals(target.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF import change sets cannot be restored");
        }
        if (StudyProgramChangeSetService.SOURCE_RESTORE_BEFORE.equals(target.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restore change sets cannot be restored");
        }
    }

    private void restoreFields(List<Long> restoreChangeSetIds, Set<Long> moduleIdsToDelete, Long restoreChangeSetId) {
        List<ModuleFieldChange> changes = fieldChangeRepository.findByChangeSetIds(restoreChangeSetIds);
        Map<ModuleFieldKey, ModuleFieldChange> oldestChangeByField = new HashMap<>();

        for (ModuleFieldChange change : changes) {
            if (change.getModuleId() == null || moduleIdsToDelete.contains(change.getModuleId())) {
                continue;
            }
            ModuleFieldKey key = new ModuleFieldKey(change.getModuleId(), change.getFieldPath());
            oldestChangeByField.putIfAbsent(key, change);
        }

        Map<Long, List<ModuleFieldChange>> changesByModule = new HashMap<>();
        for (ModuleFieldChange change : oldestChangeByField.values()) {
            changesByModule.computeIfAbsent(change.getModuleId(), ignored -> new ArrayList<>()).add(change);
        }

        for (Map.Entry<Long, List<ModuleFieldChange>> entry : changesByModule.entrySet()) {
            restoreModuleFields(entry.getKey(), entry.getValue(), restoreChangeSetId);
        }
    }

    private void restoreModuleFields(Long moduleId, List<ModuleFieldChange> changes, Long restoreChangeSetId) {
        Module oldModule = moduleRepository.findById(moduleId)
                .orElse(null);
        if (oldModule == null) {
            return;
        }

        Module oldModuleCopy = copyModule(oldModule);
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(moduleId).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(moduleId);

        Module restoredModule = copyModule(oldModule);
        ModuleWorkload restoredWorkload = oldWorkload;
        List<Long> restoredTeachingFormIds = oldTeachingFormIds;
        boolean workloadChanged = false;
        boolean teachingFormsChanged = false;

        for (ModuleFieldChange change : changes) {
            String fieldPath = change.getFieldPath();
            if ("workload".equals(fieldPath)) {
                restoredWorkload = parseWorkload(change.getOldValue());
                workloadChanged = true;
            } else if ("teachingFormIds".equals(fieldPath)) {
                restoredTeachingFormIds = parseLongList(change.getOldValue());
                teachingFormsChanged = true;
            } else if (fieldPath != null && fieldPath.startsWith("module.")) {
                applyModuleField(restoredModule, fieldPath, change.getOldValue());
            }
        }

        moduleRepository.update(moduleId, restoredModule);
        if (workloadChanged) {
            if (restoredWorkload == null) {
                workloadRepository.deleteByModuleId(moduleId);
            } else {
                workloadRepository.upsert(moduleId, restoredWorkload);
            }
        }
        if (teachingFormsChanged) {
            teachingFormRepository.replaceForModule(moduleId, restoredTeachingFormIds);
        }

        Module savedModule = moduleRepository.findById(moduleId).orElseThrow();
        ModuleWorkload savedWorkload = workloadRepository.findByModuleId(moduleId).orElse(null);
        List<Long> savedTeachingFormIds = teachingFormIdsByModuleId(moduleId);
        if (restoreChangeSetId != null) {
            changeLogService.saveChanges(
                    restoreChangeSetId,
                    moduleId,
                    oldModuleCopy,
                    savedModule,
                    oldWorkload,
                    savedWorkload,
                    oldTeachingFormIds,
                    savedTeachingFormIds
            );
        }
    }

    private void deleteCreatedModules(List<Module> modulesToDelete) {
        for (Module module : modulesToDelete) {
            if (module.getId() != null) {
                moduleRepository.delete(module.getId());
            }
        }
    }

    private void applyModuleField(Module module, String fieldPath, String oldValue) {
        JsonNode value = parseJson(oldValue);
        switch (fieldPath) {
            case "module.moduleNumber" -> module.setModuleNumber(textOrNull(value));
            case "module.name" -> module.setName(textOrNull(value));
            case "module.credits" -> module.setCredits(decimalOrNull(value));
            case "module.description" -> module.setDescription(textOrNull(value));
            case "module.moduleType" -> module.setModuleType(textOrNull(value));
            case "module.titleDe" -> module.setTitleDe(textOrNull(value));
            case "module.titleEn" -> module.setTitleEn(textOrNull(value));
            case "module.coordinator" -> module.setCoordinator(textOrNull(value));
            case "module.applicability" -> module.setApplicability(textOrNull(value));
            case "module.learningArea" -> module.setLearningArea(textOrNull(value));
            case "module.learningObjectives" -> module.setLearningObjectives(textOrNull(value));
            case "module.prerequisites" -> module.setPrerequisites(textOrNull(value));
            case "module.level" -> module.setLevel(textOrNull(value));
            case "module.moduleStatus" -> module.setModuleStatus(textOrNull(value));
            case "module.frequency" -> module.setFrequency(textOrNull(value));
            case "module.examinationForm" -> module.setExaminationForm(textOrNull(value));
            case "module.contents" -> module.setContents(textOrNull(value));
            case "module.literature" -> module.setLiterature(textOrNull(value));
            case "module.additionalNotes" -> module.setAdditionalNotes(textOrNull(value));
            case "module.moduleWeight" -> module.setModuleWeight(decimalOrNull(value));
            default -> {
            }
        }
    }

    private ModuleWorkload parseWorkload(String json) {
        if (isJsonNull(json)) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, ModuleWorkload.class);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not parse workload history");
        }
    }

    private List<Long> parseLongList(String json) {
        JsonNode value = parseJson(json);
        if (value == null || value.isNull() || !value.isArray()) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>();
        for (JsonNode item : value) {
            if (item != null && item.isNumber()) {
                ids.add(item.longValue());
            }
        }
        return ids;
    }

    private JsonNode parseJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return jsonMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not parse field change history");
        }
    }

    private boolean isJsonNull(String json) {
        return json == null || "null".equals(json);
    }

    private String textOrNull(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private BigDecimal decimalOrNull(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
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

    private String moduleLabels(List<Module> modules) {
        Set<String> labels = new LinkedHashSet<>();
        for (Module module : modules) {
            String moduleNumber = module.getModuleNumber() == null ? "" : module.getModuleNumber();
            String name = module.getName() == null ? "" : module.getName();
            String label = (moduleNumber + " " + name).trim();
            labels.add(label.isBlank() ? "module " + module.getId() : label);
        }
        return String.join(", ", labels);
    }

    private record ModuleFieldKey(Long moduleId, String fieldPath) {
    }
}
