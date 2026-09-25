package de.bht.modulebook.module.version;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.module.workload.PresenceHourComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ModuleChangeLogService {
    public static final String MODULE_CREATED_FIELD = "module.created";
    public static final String MODULE_DELETED_FIELD = "module.deleted";

    private final ModuleFieldChangeRepository repository;
    private final JsonMapper jsonMapper;

    public ModuleChangeLogService(ModuleFieldChangeRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public static boolean isLifecycleChange(String fieldPath) {
        return MODULE_CREATED_FIELD.equals(fieldPath) || MODULE_DELETED_FIELD.equals(fieldPath);
    }

    public void saveModuleCreated(Long changeSetId, Module module) {
        repository.insert(
                changeSetId,
                module.getId(),
                MODULE_CREATED_FIELD,
                toJson(null),
                toJson(CreatedModuleSnapshot.from(module))
        );
    }

    public void saveModuleDeleted(
            Long changeSetId,
            Module module,
            ModuleWorkload workload,
            List<Long> teachingFormIds
    ) {
        repository.insert(
                changeSetId,
                module.getId(),
                MODULE_DELETED_FIELD,
                toJson(DeletedModuleSnapshot.from(module, workload, teachingFormIds)),
                toJson(null)
        );
    }

    public void saveChanges(
            Long changeSetId,
            Long moduleId,
            Module oldModule,
            Module newModule,
            ModuleWorkload oldWorkload,
            ModuleWorkload newWorkload,
            List<Long> oldTeachingFormIds,
            List<Long> newTeachingFormIds
    ) {
        saveChanges(changeSetId, moduleId, oldModule, newModule, oldWorkload, newWorkload,
                oldTeachingFormIds, newTeachingFormIds, null);
    }

    public void saveChanges(
            Long changeSetId,
            Long moduleId,
            Module oldModule,
            Module newModule,
            ModuleWorkload oldWorkload,
            ModuleWorkload newWorkload,
            List<Long> oldTeachingFormIds,
            List<Long> newTeachingFormIds,
            List<String> changedFieldPaths
    ) {
        Set<String> allowedPaths = changedFieldPaths == null ? null : new HashSet<>(changedFieldPaths);
        addIfChanged(changeSetId, moduleId, "module.moduleNumber", oldModule.getModuleNumber(), newModule.getModuleNumber(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.title", oldModule.getTitle(), newModule.getTitle(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.credits", oldModule.getCredits(), newModule.getCredits(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.moduleType", oldModule.getModuleType(), newModule.getModuleType(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.coordinator", oldModule.getCoordinator(), newModule.getCoordinator(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.applicability", oldModule.getApplicability(), newModule.getApplicability(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.learningArea", oldModule.getLearningArea(), newModule.getLearningArea(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.learningObjectives", oldModule.getLearningObjectives(), newModule.getLearningObjectives(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.prerequisites", oldModule.getPrerequisites(), newModule.getPrerequisites(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.level", oldModule.getLevel(), newModule.getLevel(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.moduleStatus", oldModule.getModuleStatus(), newModule.getModuleStatus(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.moduleComments", oldModule.getModuleComments(), newModule.getModuleComments(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.frequency", oldModule.getFrequency(), newModule.getFrequency(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.examinationForm", oldModule.getExaminationForm(), newModule.getExaminationForm(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.contents", oldModule.getContents(), newModule.getContents(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.literature", oldModule.getLiterature(), newModule.getLiterature(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.additionalNotes", oldModule.getAdditionalNotes(), newModule.getAdditionalNotes(), allowedPaths);
        addIfChanged(changeSetId, moduleId, "module.moduleWeight", oldModule.getModuleWeight(), newModule.getModuleWeight(), allowedPaths);

        addIfChanged(changeSetId, moduleId, "workload", oldWorkload, newWorkload, allowedPaths);
        addIfChanged(changeSetId, moduleId, "teachingFormIds", oldTeachingFormIds, newTeachingFormIds, allowedPaths);
    }

    private void addIfChanged(Long changeSetId, Long moduleId, String fieldPath, Object oldValue, Object newValue) {
        addIfChanged(changeSetId, moduleId, fieldPath, oldValue, newValue, null);
    }

    private void addIfChanged(Long changeSetId, Long moduleId, String fieldPath, Object oldValue, Object newValue, Set<String> allowedPaths) {
        if (allowedPaths != null && !allowedPaths.contains(fieldPath)) {
            return;
        }
        if (sameValue(oldValue, newValue)) {
            return;
        }

        repository.insert(
                changeSetId,
                moduleId,
                fieldPath,
                toJson(oldValue),
                toJson(newValue)
        );
    }

    private boolean sameValue(Object oldValue, Object newValue) {
        if (oldValue instanceof BigDecimal oldDecimal && newValue instanceof BigDecimal newDecimal) {
            return oldDecimal.compareTo(newDecimal) == 0;
        }
        if (oldValue instanceof String || newValue instanceof String) {
            return sameText((String) oldValue, (String) newValue);
        }
        if (oldValue instanceof ModuleWorkload || newValue instanceof ModuleWorkload) {
            return sameWorkload((ModuleWorkload) oldValue, (ModuleWorkload) newValue);
        }
        if (oldValue instanceof List<?> || newValue instanceof List<?>) {
            return sameNumberList(oldValue, newValue);
        }
        return Objects.equals(oldValue, newValue);
    }

    private boolean sameWorkload(ModuleWorkload oldWorkload, ModuleWorkload newWorkload) {
        if (oldWorkload == null || newWorkload == null) {
            return oldWorkload == newWorkload;
        }
        if (!sameDecimal(oldWorkload.getPresenceHours(), newWorkload.getPresenceHours())) {
            return false;
        }
        if (!sameDecimal(oldWorkload.getSelfStudyHours(), newWorkload.getSelfStudyHours())) {
            return false;
        }

        List<PresenceHourComponent> oldComponents = normalizedComponents(oldWorkload.getComponents());
        List<PresenceHourComponent> newComponents = normalizedComponents(newWorkload.getComponents());
        if (oldComponents.size() != newComponents.size()) {
            return false;
        }

        for (int index = 0; index < oldComponents.size(); index++) {
            PresenceHourComponent oldComponent = oldComponents.get(index);
            PresenceHourComponent newComponent = newComponents.get(index);
            if (!Objects.equals(oldComponent.getTeachingFormId(), newComponent.getTeachingFormId())) {
                return false;
            }
            if (!sameDecimal(oldComponent.getSwsAmount(), newComponent.getSwsAmount())) {
                return false;
            }
        }

        return true;
    }

    private List<PresenceHourComponent> normalizedComponents(List<PresenceHourComponent> components) {
        if (components == null) {
            return List.of();
        }
        return components.stream()
                .sorted(Comparator
                        .comparing(PresenceHourComponent::getTeachingFormId, Comparator.nullsFirst(Long::compareTo))
                        .thenComparing(PresenceHourComponent::getSwsAmount, Comparator.nullsFirst(BigDecimal::compareTo)))
                .toList();
    }

    private boolean sameDecimal(BigDecimal oldDecimal, BigDecimal newDecimal) {
        if (oldDecimal == null || newDecimal == null) {
            return oldDecimal == newDecimal;
        }
        return oldDecimal.compareTo(newDecimal) == 0;
    }

    private boolean sameText(String oldText, String newText) {
        String normalizedOldText = normalizeBlankText(oldText);
        String normalizedNewText = normalizeBlankText(newText);
        return Objects.equals(normalizedOldText, normalizedNewText);
    }

    private String normalizeBlankText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value;
    }

    private boolean sameNumberList(Object oldValue, Object newValue) {
        if (oldValue == null || newValue == null) {
            return oldValue == newValue || isEmptyList(oldValue) || isEmptyList(newValue);
        }
        if (!(oldValue instanceof List<?> oldList) || !(newValue instanceof List<?> newList)) {
            return false;
        }
        if (!containsOnlyNumbers(oldList) || !containsOnlyNumbers(newList)) {
            return Objects.equals(oldValue, newValue);
        }

        List<Long> oldIds = normalizedNumberList(oldList);
        List<Long> newIds = normalizedNumberList(newList);
        return Objects.equals(oldIds, newIds);
    }

    private boolean isEmptyList(Object value) {
        return value instanceof List<?> list && list.isEmpty();
    }

    private boolean containsOnlyNumbers(List<?> values) {
        return values.stream().allMatch(value -> value instanceof Number);
    }

    private List<Long> normalizedNumberList(List<?> values) {
        return values.stream()
                .map(value -> ((Number) value).longValue())
                .sorted()
                .toList();
    }

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not serialize field change");
        }
    }

    private record CreatedModuleSnapshot(
            Long id,
            String moduleNumber,
            String title,
            BigDecimal credits,
            String moduleType
    ) {
        private static CreatedModuleSnapshot from(Module module) {
            return new CreatedModuleSnapshot(
                    module.getId(),
                    module.getModuleNumber(),
                    module.getTitle(),
                    module.getCredits(),
                    module.getModuleType()
            );
        }
    }

    private record DeletedModuleSnapshot(
            Module module,
            ModuleWorkload workload,
            List<Long> teachingFormIds
    ) {
        private static DeletedModuleSnapshot from(
                Module module,
                ModuleWorkload workload,
                List<Long> teachingFormIds
        ) {
            return new DeletedModuleSnapshot(
                    module,
                    workload,
                    teachingFormIds == null ? List.of() : teachingFormIds
            );
        }
    }
}
