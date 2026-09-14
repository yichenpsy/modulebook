package de.bht.modulebook.module.version;

import java.time.Instant;

public class ModuleFieldChange {
    private Long id;
    private Long changeSetId;
    private Long moduleId;
    private String fieldPath;
    private String oldValue;
    private String newValue;
    private Instant createdAt;

    public ModuleFieldChange(Long id, Long changeSetId, Long moduleId, String fieldPath,
                             String oldValue, String newValue, Instant createdAt) {
        this.id = id;
        this.changeSetId = changeSetId;
        this.moduleId = moduleId;
        this.fieldPath = fieldPath;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getChangeSetId() { return changeSetId; }
    public Long getModuleId() { return moduleId; }
    public String getFieldPath() { return fieldPath; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public Instant getCreatedAt() { return createdAt; }
}