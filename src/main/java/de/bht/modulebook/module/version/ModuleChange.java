package de.bht.modulebook.module.version;

import java.time.Instant;

public class ModuleChange {
    private Long id;
    private Long changeSetId;
    private Long moduleId;
    private String changePath;
    private String oldValue;
    private String newValue;
    private Instant createdAt;

    public ModuleChange(Long id, Long changeSetId, Long moduleId, String changePath,
                        String oldValue, String newValue, Instant createdAt) {
        this.id = id;
        this.changeSetId = changeSetId;
        this.moduleId = moduleId;
        this.changePath = changePath;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getChangeSetId() { return changeSetId; }
    public Long getModuleId() { return moduleId; }
    public String getChangePath() { return changePath; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public Instant getCreatedAt() { return createdAt; }
}
