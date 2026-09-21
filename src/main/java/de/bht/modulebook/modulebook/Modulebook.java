package de.bht.modulebook.modulebook;

import java.time.Instant;

public class Modulebook {
    private final Long id;
    private final String studyProgramName;
    private final String degreeType;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Modulebook(
            Long id,
            String studyProgramName,
            String degreeType,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.studyProgramName = studyProgramName;
        this.degreeType = degreeType;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getStudyProgramName() {
        return studyProgramName;
    }

    public String getDegreeType() {
        return degreeType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
