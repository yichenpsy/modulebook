package de.bht.modulebook.studyprogram.changeset;

import java.time.Instant;

public class StudyProgramChangeSet {
    private Long id;
    private Long studyProgramId;
    private String sourceType;
    private String reason;
    private Instant createdAt;

    public StudyProgramChangeSet(Long id, Long studyProgramId, String sourceType, String reason, Instant createdAt) {
        this.id = id;
        this.studyProgramId = studyProgramId;
        this.sourceType = sourceType;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getStudyProgramId() { return studyProgramId; }
    public String getSourceType() { return sourceType; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
