package de.bht.modulebook.modulebook;

import java.time.Instant;

public class ModulebookTag {
    private final Long id;
    private final Long studyProgramId;
    private final String tagMessage;
    private final String snapshotJson;
    private final Instant createdAt;
    private final String sourceType;
    private final Long sourceChangeSetId;

    public ModulebookTag(Long id, Long studyProgramId, String tagMessage, String snapshotJson, Instant createdAt) {
        this(id, studyProgramId, tagMessage, snapshotJson, createdAt, "MANUAL", null);
    }

    public ModulebookTag(
            Long id,
            Long studyProgramId,
            String tagMessage,
            String snapshotJson,
            Instant createdAt,
            String sourceType,
            Long sourceChangeSetId
    ) {
        this.id = id;
        this.studyProgramId = studyProgramId;
        this.tagMessage = tagMessage;
        this.snapshotJson = snapshotJson;
        this.createdAt = createdAt;
        this.sourceType = sourceType;
        this.sourceChangeSetId = sourceChangeSetId;
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
}
