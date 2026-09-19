package de.bht.modulebook.modulebook;

import java.time.Instant;
import java.time.LocalDate;

public class ModulebookTag {
    private final Long id;
    private final Long studyProgramId;
    private final String tagMessage;
    private final String snapshotJson;
    private final Instant createdAt;
    private final String sourceType;
    private final Long sourceChangeSetId;
    private final LocalDate modulebookReleaseDate;
    private final LocalDate studyExaminationRegulationsDate;

    public ModulebookTag(Long id, Long studyProgramId, String tagMessage, String snapshotJson, Instant createdAt) {
        this(id, studyProgramId, tagMessage, snapshotJson, createdAt, "MANUAL", null, null, null);
    }

    public ModulebookTag(
            Long id,
            Long studyProgramId,
            String tagMessage,
            String snapshotJson,
            Instant createdAt,
            String sourceType,
            Long sourceChangeSetId,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate
    ) {
        this.id = id;
        this.studyProgramId = studyProgramId;
        this.tagMessage = tagMessage;
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

    public Long getStudyProgramId() {
        return studyProgramId;
    }

    public String getTagMessage() {
        return tagMessage;
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
