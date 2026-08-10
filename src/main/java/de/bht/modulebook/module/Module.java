package de.bht.modulebook.module;
import java.math.BigDecimal;
import java.time.Instant;

public class Module {

    private Long id;
    private Long studyProgramId;
    private String code;
    private String name;
    private BigDecimal credits;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public Module() {
    }

    public Module(Long id, Long studyProgramId, String code, String name, BigDecimal credits, String description,
            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.studyProgramId = studyProgramId;
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudyProgramId() {
        return studyProgramId;
    }

    public void setStudyProgramId(Long studyProgramId) {
        this.studyProgramId = studyProgramId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getCredits() {
        return credits;
    }

    public void setCredits(BigDecimal credits) {
        this.credits = credits;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

}
