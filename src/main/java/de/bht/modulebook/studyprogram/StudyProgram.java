package de.bht.modulebook.studyprogram;

import java.time.Instant;
import java.time.LocalDate;

public class StudyProgram {

    private Long id;
    private String name;
    private String degreeType;
    private LocalDate modulebookReleaseDate;
    private LocalDate studyExaminationRegulationsDate;
    private Instant createdAt;
    private Instant updatedAt;

    public StudyProgram() {
    }

    public StudyProgram(
            Long id,
            String name,
            String degreeType,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.degreeType = degreeType;
        this.modulebookReleaseDate = modulebookReleaseDate;
        this.studyExaminationRegulationsDate = studyExaminationRegulationsDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDegreeType() {
        return degreeType;
    }

    public void setDegreeType(String degreeType) {
        this.degreeType = degreeType;
    }

    public LocalDate getModulebookReleaseDate() {
        return modulebookReleaseDate;
    }

    public void setModulebookReleaseDate(LocalDate modulebookReleaseDate) {
        this.modulebookReleaseDate = modulebookReleaseDate;
    }

    public LocalDate getStudyExaminationRegulationsDate() {
        return studyExaminationRegulationsDate;
    }

    public void setStudyExaminationRegulationsDate(LocalDate studyExaminationRegulationsDate) {
        this.studyExaminationRegulationsDate = studyExaminationRegulationsDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
