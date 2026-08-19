package de.bht.modulebook.teachingform.dto;

public record TeachingFormResponse(
    Long id,
    String code,
    String labelDe,
    String labelEn
) {
}
