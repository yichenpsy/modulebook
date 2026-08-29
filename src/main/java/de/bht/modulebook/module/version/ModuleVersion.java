package de.bht.modulebook.module.version;

import java.time.Instant;

public class ModuleVersion {
    private Long id;
    private Long moduleId;
    private Long studyProgramId;
    private Long changeSetId;
    private Integer versionNumber;
    private String snapshotJson;
    private String reason;
    private Instant createdAt;

    public ModuleVersion(Long id, Long moduleId, Integer versionNumber,
                         String snapshotJson, String reason, Instant createdAt) {
        this(id, moduleId, null, null, versionNumber, snapshotJson, reason, createdAt);
    }

    public ModuleVersion(Long id, Long moduleId, Long studyProgramId, Integer versionNumber,
                         String snapshotJson, String reason, Instant createdAt) {
        this(id, moduleId, studyProgramId, null, versionNumber, snapshotJson, reason, createdAt);
    }

    public ModuleVersion(Long id, Long moduleId, Long studyProgramId, Long changeSetId, Integer versionNumber,
                         String snapshotJson, String reason, Instant createdAt) {
        this.id = id;
        this.moduleId = moduleId;
        this.studyProgramId = studyProgramId;
        this.changeSetId = changeSetId;
        this.versionNumber = versionNumber;
        this.snapshotJson = snapshotJson;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getModuleId() { return moduleId; }
    public Long getStudyProgramId() { return studyProgramId; }
    public Long getChangeSetId() { return changeSetId; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
