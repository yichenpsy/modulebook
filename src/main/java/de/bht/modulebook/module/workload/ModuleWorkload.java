package de.bht.modulebook.module.workload;

import java.math.BigDecimal;
import java.util.List;

public class ModuleWorkload {
    private Long id;
    private Long moduleId;
    private BigDecimal presenceHours;
    private BigDecimal selfStudyHours;
    private String rawText;
    private List<PresenceHourComponent> components = List.of();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public BigDecimal getPresenceHours() {
        return presenceHours;
    }

    public void setPresenceHours(BigDecimal presenceHours) {
        this.presenceHours = presenceHours;
    }

    public BigDecimal getSelfStudyHours() {
        return selfStudyHours;
    }

    public void setSelfStudyHours(BigDecimal selfStudyHours) {
        this.selfStudyHours = selfStudyHours;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public List<PresenceHourComponent> getComponents() {
        return components;
    }

    public void setComponents(List<PresenceHourComponent> components) {
        this.components = components == null ? List.of() : components;
    }

}