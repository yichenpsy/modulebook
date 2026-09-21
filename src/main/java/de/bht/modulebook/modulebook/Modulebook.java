package de.bht.modulebook.modulebook;

import java.time.Instant;
import java.time.LocalDate;

public class Modulebook {
    private final Long id;
    private final Long studyProgramId;
    private final String title;
    private final String status;
    private final LocalDate modulebookReleaseDate;
    private final LocalDate studyExaminationRegulationsDate;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Modulebook(
            Long id,
            Long studyProgramId,
            String title,
            String status,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.studyProgramId = studyProgramId;
        this.title = title;
        this.status = status;
        this.modulebookReleaseDate = modulebookReleaseDate;
        this.studyExaminationRegulationsDate = studyExaminationRegulationsDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getStudyProgramId() {
        return studyProgramId;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getModulebookReleaseDate() {
        return modulebookReleaseDate;
    }

    public LocalDate getStudyExaminationRegulationsDate() {
        return studyExaminationRegulationsDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
