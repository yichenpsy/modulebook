package de.bht.modulebook.module.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import de.bht.modulebook.module.workload.dto.ModuleWorkloadResponse;
import de.bht.modulebook.teachingform.dto.TeachingFormResponse;

// shape of JSON returned to frontend
public record ModuleResponse(
    Long id,
    Long modulebookId,
    String moduleNumber,
    String name,
    BigDecimal credits,
    String description,
    String moduleType,
    String titleDe,
    String titleEn,
    String coordinator,
    String applicability,
    String learningArea,
    String learningObjectives,
    String prerequisites,
    String level,
    String moduleStatus,
    String moduleComments,
    String frequency,
    String examinationForm,
    String contents,
    String literature,
    String additionalNotes,
    BigDecimal moduleWeight,
    Instant createdAt,
    Instant updatedAt,
    List<TeachingFormResponse> teachingForms,
    ModuleWorkloadResponse workload
) {    
}
