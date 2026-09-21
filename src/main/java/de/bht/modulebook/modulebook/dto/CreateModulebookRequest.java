package de.bht.modulebook.modulebook.dto;

public record CreateModulebookRequest(
        String studyProgramName,
        String name,
        String degreeType
) {
    public CreateModulebookRequest(String name, String degreeType) {
        this(null, name, degreeType);
    }

    public String requestedStudyProgramName() {
        return studyProgramName != null && !studyProgramName.isBlank() ? studyProgramName : name;
    }
}
