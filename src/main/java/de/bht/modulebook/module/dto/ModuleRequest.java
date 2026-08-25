package de.bht.modulebook.module.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import de.bht.modulebook.module.workload.dto.ModuleWorkloadRequest;

// shape of JSON received from frontend when creating/updating a module.
public record ModuleRequest(
    @NotNull(message = "Study program id is required")
    Long studyProgramId,

    @NotBlank(message = "Module number is required")
    @Size(max = 50, message = "Module number must be at most 50 characters")
    String moduleNumber,

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    String name,
    @NotNull(message = "Credits are required")
    BigDecimal credits,
    String description,
    Long modulebookId,
    String moduleType,
    String titleDe,
    String titleEn,
    String coordinator,
    String applicability,
    String learningArea,
    String learningObjectives,
    String prerequisites,
    String level,
    String status,
    String frequency,
    String contents,
    String literature,
    String additionalNotes,
    BigDecimal moduleWeight,
    List<Long> teachingFormIds,
    ModuleWorkloadRequest workload,
    String examinationForm,
    String versionReason
){
}
