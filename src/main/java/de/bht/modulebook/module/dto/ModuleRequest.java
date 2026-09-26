package de.bht.modulebook.module.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import de.bht.modulebook.module.workload.dto.ModuleWorkloadRequest;

// shape of JSON received from frontend when creating/updating a module.
public record ModuleRequest(
    @NotNull(message = "Modulebook id is required")
    Long modulebookId,

    @NotBlank(message = "Module number is required")
    @Size(max = 50, message = "Module number must be at most 50 characters")
    String moduleNumber,

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    String title,
    @NotNull(message = "Credits are required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Credits must be non-negative")
    BigDecimal credits,
    String moduleType,
    String coordinator,
    String applicability,
    String learningArea,
    String learningObjectives,
    String prerequisites,
    String level,
    String moduleStatus,
    String moduleComments,
    String frequency,
    String contents,
    String literature,
    String additionalNotes,
    BigDecimal moduleWeight,
    List<Long> teachingFormIds,
    ModuleWorkloadRequest workload,
    String examinationForm,
    String versionReason,
    List<String> changedFieldPaths
){
}
