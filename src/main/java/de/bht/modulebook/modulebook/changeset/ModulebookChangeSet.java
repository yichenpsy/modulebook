package de.bht.modulebook.modulebook.changeset;

import java.time.Instant;

public class ModulebookChangeSet {
    private Long id;
    private Long modulebookId;
    private String sourceType;
    private String reason;
    private Instant createdAt;
    private Instant discardedAt;
    private Long discardedByChangeSetId;

    public ModulebookChangeSet(Long id, Long modulebookId, String sourceType, String reason, Instant createdAt) {
        this(id, modulebookId, sourceType, reason, createdAt, null, null);
    }

    public ModulebookChangeSet(
            Long id,
            Long modulebookId,
            String sourceType,
            String reason,
            Instant createdAt,
            Instant discardedAt,
            Long discardedByChangeSetId
    ) {
        this.id = id;
        this.modulebookId = modulebookId;
        this.sourceType = sourceType;
        this.reason = reason;
        this.createdAt = createdAt;
        this.discardedAt = discardedAt;
        this.discardedByChangeSetId = discardedByChangeSetId;
    }

    public Long getId() { return id; }
    public Long getModulebookId() { return modulebookId; }
    public String getSourceType() { return sourceType; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDiscardedAt() { return discardedAt; }
    public Long getDiscardedByChangeSetId() { return discardedByChangeSetId; }
}
