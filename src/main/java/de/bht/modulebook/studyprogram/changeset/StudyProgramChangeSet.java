package de.bht.modulebook.studyprogram.changeset;

import java.time.Instant;

public class StudyProgramChangeSet {
    private Long id;
    private Long studyProgramId;
    private String sourceType;
    private String reason;
    private Instant createdAt;
    private Instant discardedAt;
    private Long discardedByChangeSetId;

    public StudyProgramChangeSet(Long id, Long studyProgramId, String sourceType, String reason, Instant createdAt) {
        this(id, studyProgramId, sourceType, reason, createdAt, null, null);
    }

    public StudyProgramChangeSet(
            Long id,
            Long studyProgramId,
            String sourceType,
            String reason,
            Instant createdAt,
            Instant discardedAt,
            Long discardedByChangeSetId
    ) {
        this.id = id;
        this.studyProgramId = studyProgramId;
        this.sourceType = sourceType;
        this.reason = reason;
        this.createdAt = createdAt;
        this.discardedAt = discardedAt;
        this.discardedByChangeSetId = discardedByChangeSetId;
    }

    public Long getId() { return id; }
    public Long getStudyProgramId() { return studyProgramId; }
    public String getSourceType() { return sourceType; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDiscardedAt() { return discardedAt; }
    public Long getDiscardedByChangeSetId() { return discardedByChangeSetId; }
}
