package de.bht.modulebook.module.version;

import java.time.Instant;

public class ModuleVersion {
    private Long id;
    private Long moduleId;
    private Integer versionNumber;
    private String snapshotJson;
    private String reason;
    private Instant createdAt;

    public ModuleVersion(Long id, Long moduleId, Integer versionNumber,
                         String snapshotJson, String reason, Instant createdAt) {
        this.id = id;
        this.moduleId = moduleId;
        this.versionNumber = versionNumber;
        this.snapshotJson = snapshotJson;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getModuleId() { return moduleId; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}