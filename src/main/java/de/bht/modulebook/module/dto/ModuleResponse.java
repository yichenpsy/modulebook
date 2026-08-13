package de.bht.modulebook.module.dto;

import java.math.BigDecimal;
import java.time.Instant;

// shape of JSON returned to frontend
public record ModuleResponse(
    Long id,
    Long studyProgramId,
    String moduleNumber,
    String name,
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
    Instant createdAt,
    Instant updatedAt
) {    
}
