package de.bht.modulebook.module;

import java.util.List;

import de.bht.modulebook.module.dto.ModuleRequest;
import de.bht.modulebook.module.dto.ModuleResponse;

import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.module.workload.ModuleWorkloadMapper;
import de.bht.modulebook.teachingform.TeachingForm;
import de.bht.modulebook.teachingform.TeachingFormMapper;

// converts between ModuleRequest, Module, ModuleResponse
public class ModuleMapper {
    // takes JSON request data and builds a Module object the service can save
    public static Module toEntity(ModuleRequest request) {
        Module module = new Module();
        module.setStudyProgramId(request.studyProgramId());
        module.setModuleNumber(request.moduleNumber());
        module.setName(request.name());
        module.setCredits(request.credits());
        module.setDescription(request.description());
        module.setModulebookId(request.modulebookId());
        module.setModuleType(request.moduleType());
        module.setTitleDe(request.titleDe());
        module.setTitleEn(request.titleEn());
        module.setCoordinator(request.coordinator());
        module.setApplicability(request.applicability());
        module.setLearningArea(request.learningArea());
        module.setLearningObjectives(request.learningObjectives());
        module.setPrerequisites(request.prerequisites());
        module.setLevel(request.level());
        module.setStatus(request.status());
        module.setFrequency(request.frequency());
        module.setContents(request.contents());
        module.setLiterature(request.literature());
        module.setAdditionalNotes(request.additionalNotes());
        module.setModuleWeight(request.moduleWeight());
        module.setExaminationForm(request.examinationForm());
        return module;
    }

    //return module, but no workload.
    public static ModuleResponse toResponse(Module module) {
    return toResponse(module, null, List.of());
    }

    // takes the saved Module and builds the response object for the API.
    public static ModuleResponse toResponse(Module module, ModuleWorkload workload) {
        return toResponse(module, workload, List.of());
    }

    public static ModuleResponse toResponse(
        Module module,
        ModuleWorkload workload,
        List<TeachingForm> teachingForms
    ) {
        return new ModuleResponse(
            module.getId(),
            module.getStudyProgramId(),
            module.getModuleNumber(),
            module.getName(),
            module.getCredits(),
            module.getDescription(),
            module.getModulebookId(),
            module.getModuleType(),
            module.getTitleDe(),
            module.getTitleEn(),
            module.getCoordinator(),
            module.getApplicability(),
            module.getLearningArea(),
            module.getLearningObjectives(),
            module.getPrerequisites(),
            module.getLevel(),
            module.getStatus(),
            module.getFrequency(),
            module.getExaminationForm(),
            module.getContents(),
            module.getLiterature(),
            module.getAdditionalNotes(),
            module.getModuleWeight(),
            module.getCreatedAt(),
            module.getUpdatedAt(),
            teachingForms.stream()
                .map(TeachingFormMapper::toResponse)
                .toList(),
            ModuleWorkloadMapper.toResponse(workload)
        );
    }
}
