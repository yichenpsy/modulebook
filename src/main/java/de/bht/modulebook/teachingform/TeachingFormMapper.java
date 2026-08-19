package de.bht.modulebook.teachingform;

import de.bht.modulebook.teachingform.dto.TeachingFormResponse;

public class TeachingFormMapper {
    public static TeachingFormResponse toResponse(TeachingForm teachingForm) {
        return new TeachingFormResponse(
                teachingForm.getId(),
                teachingForm.getCode(),
                teachingForm.getLabelDe(),
                teachingForm.getLabelEn()
        );
    }
}
