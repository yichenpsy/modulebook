package de.bht.modulebook.modulebook;

import java.time.Instant;
import java.time.LocalDate;

public class ModulebookVersion {
    private final Long id;
    private final Long modulebookId;
    private final Integer versionNumber;
    private final String versionMessage;
    private final String snapshotJson;
    private final Instant createdAt;
    private final String sourceType;
    private final Long sourceChangeSetId;
    private final LocalDate modulebookReleaseDate;
    private final LocalDate studyExaminationRegulationsDate;

    public ModulebookVersion(Long id, Long modulebookId, String versionMessage, String snapshotJson, Instant createdAt) {
        this(id, modulebookId, null, versionMessage, snapshotJson, createdAt, "MANUAL", null, null, null);
    }

    public ModulebookVersion(
            Long id,
            Long modulebookId,
            Integer versionNumber,
            String versionMessage,
            String snapshotJson,
            Instant createdAt,
            String sourceType,
            Long sourceChangeSetId,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate
    ) {
        this.id = id;
        this.modulebookId = modulebookId;
        this.versionNumber = versionNumber;
        this.versionMessage = versionMessage;
        this.snapshotJson = snapshotJson;
        this.createdAt = createdAt;
        this.sourceType = sourceType;
        this.sourceChangeSetId = sourceChangeSetId;
        this.modulebookReleaseDate = modulebookReleaseDate;
        this.studyExaminationRegulationsDate = studyExaminationRegulationsDate;
    }

    public Long getId() {
        return id;
    }

    public Long getModulebookId() {
        return modulebookId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public String getVersionMessage() {
        return versionMessage;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceChangeSetId() {
        return sourceChangeSetId;
    }

    public LocalDate getModulebookReleaseDate() {
        return modulebookReleaseDate;
    }

    public LocalDate getStudyExaminationRegulationsDate() {
        return studyExaminationRegulationsDate;
    }
}
