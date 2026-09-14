package de.bht.modulebook.module.version;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
    private final ModuleFieldChangeRepository repository;
    private final JsonMapper jsonMapper;

    public ModuleChangeLogService(ModuleFieldChangeRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
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
        addIfChanged(changeSetId, moduleId, "module.moduleNumber", oldModule.getModuleNumber(), newModule.getModuleNumber());
        addIfChanged(changeSetId, moduleId, "module.name", oldModule.getName(), newModule.getName());
        addIfChanged(changeSetId, moduleId, "module.credits", oldModule.getCredits(), newModule.getCredits());
        addIfChanged(changeSetId, moduleId, "module.description", oldModule.getDescription(), newModule.getDescription());
        addIfChanged(changeSetId, moduleId, "module.moduleType", oldModule.getModuleType(), newModule.getModuleType());
        addIfChanged(changeSetId, moduleId, "module.titleDe", oldModule.getTitleDe(), newModule.getTitleDe());
        addIfChanged(changeSetId, moduleId, "module.titleEn", oldModule.getTitleEn(), newModule.getTitleEn());
        addIfChanged(changeSetId, moduleId, "module.coordinator", oldModule.getCoordinator(), newModule.getCoordinator());
        addIfChanged(changeSetId, moduleId, "module.applicability", oldModule.getApplicability(), newModule.getApplicability());
        addIfChanged(changeSetId, moduleId, "module.learningArea", oldModule.getLearningArea(), newModule.getLearningArea());
        addIfChanged(changeSetId, moduleId, "module.learningObjectives", oldModule.getLearningObjectives(), newModule.getLearningObjectives());
        addIfChanged(changeSetId, moduleId, "module.prerequisites", oldModule.getPrerequisites(), newModule.getPrerequisites());
        addIfChanged(changeSetId, moduleId, "module.level", oldModule.getLevel(), newModule.getLevel());
        addIfChanged(changeSetId, moduleId, "module.moduleStatus", oldModule.getModuleStatus(), newModule.getModuleStatus());
        addIfChanged(changeSetId, moduleId, "module.frequency", oldModule.getFrequency(), newModule.getFrequency());
        addIfChanged(changeSetId, moduleId, "module.examinationForm", oldModule.getExaminationForm(), newModule.getExaminationForm());
        addIfChanged(changeSetId, moduleId, "module.contents", oldModule.getContents(), newModule.getContents());
        addIfChanged(changeSetId, moduleId, "module.literature", oldModule.getLiterature(), newModule.getLiterature());
        addIfChanged(changeSetId, moduleId, "module.additionalNotes", oldModule.getAdditionalNotes(), newModule.getAdditionalNotes());
        addIfChanged(changeSetId, moduleId, "module.moduleWeight", oldModule.getModuleWeight(), newModule.getModuleWeight());

        addIfChanged(changeSetId, moduleId, "workload", oldWorkload, newWorkload);
        addIfChanged(changeSetId, moduleId, "teachingFormIds", oldTeachingFormIds, newTeachingFormIds);
    }

    private void addIfChanged(Long changeSetId, Long moduleId, String fieldPath, Object oldValue, Object newValue) {
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
}
