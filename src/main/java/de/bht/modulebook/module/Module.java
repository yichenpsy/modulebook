package de.bht.modulebook.module;
import java.math.BigDecimal;
import java.time.Instant;

// internal java object for one module
public class Module {

    private Long id;
    private Long studyProgramId;
    private String moduleNumber;
    private String name;
    private BigDecimal credits;
    private String description;
    private Long modulebookId;
    private String moduleType;
    private String titleDe;
    private String titleEn;
    private String coordinator;
    private String applicability;
    private String learningArea;
    private String learningObjectives;
    private String prerequisites;
    private String level;
    private String moduleStatus;
    private String frequency;
    private String contents;
    private String literature;
    private String additionalNotes;
    private BigDecimal moduleWeight;
    private Instant createdAt;
    private Instant updatedAt;
    private String examinationForm;
    private Long createdChangeSetId;

    public Module() {
    }

    public Module(Long id, Long studyProgramId, String moduleNumber, String name, BigDecimal credits,
            String description, Long modulebookId, String moduleType, String titleDe, String titleEn,
            String coordinator, String applicability, String learningArea, String learningObjectives,
            String prerequisites, String level, String moduleStatus, String frequency, String examinationForm, String contents,
            String literature, String additionalNotes, BigDecimal moduleWeight, Instant createdAt,
            Instant updatedAt) {
        this(id, studyProgramId, moduleNumber, name, credits, description, modulebookId, moduleType, titleDe,
                titleEn, coordinator, applicability, learningArea, learningObjectives, prerequisites, level,
                moduleStatus, frequency, examinationForm, contents, literature, additionalNotes, moduleWeight,
                createdAt, updatedAt, null);
    }

    public Module(Long id, Long studyProgramId, String moduleNumber, String name, BigDecimal credits,
            String description, Long modulebookId, String moduleType, String titleDe, String titleEn,
            String coordinator, String applicability, String learningArea, String learningObjectives,
            String prerequisites, String level, String moduleStatus, String frequency, String examinationForm, String contents,
            String literature, String additionalNotes, BigDecimal moduleWeight, Instant createdAt,
            Instant updatedAt, Long createdChangeSetId) {
        this.id = id;
        this.studyProgramId = studyProgramId;
        this.moduleNumber = moduleNumber;
        this.name = name;
        this.credits = credits;
        this.description = description;
        this.modulebookId = modulebookId;
        this.moduleType = moduleType;
        this.titleDe = titleDe;
        this.titleEn = titleEn;
        this.coordinator = coordinator;
        this.applicability = applicability;
        this.learningArea = learningArea;
        this.learningObjectives = learningObjectives;
        this.prerequisites = prerequisites;
        this.level = level;
        this.moduleStatus = moduleStatus;
        this.frequency = frequency;
        this.examinationForm = examinationForm;
        this.contents = contents;
        this.literature = literature;
        this.additionalNotes = additionalNotes;
        this.moduleWeight = moduleWeight;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdChangeSetId = createdChangeSetId;
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

    public String getModuleNumber() {
        return moduleNumber;
    }

    public void setModuleNumber(String moduleNumber) {
        this.moduleNumber = moduleNumber;
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

    public Long getModulebookId() {
        return modulebookId;
    }

    public void setModulebookId(Long modulebookId) {
        this.modulebookId = modulebookId;
    }

    public String getModuleType() {
        return moduleType;
    }

    public void setModuleType(String moduleType) {
        this.moduleType = moduleType;
    }

    public String getTitleDe() {
        return titleDe;
    }

    public void setTitleDe(String titleDe) {
        this.titleDe = titleDe;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(String coordinator) {
        this.coordinator = coordinator;
    }

    public String getApplicability() {
        return applicability;
    }

    public void setApplicability(String applicability) {
        this.applicability = applicability;
    }

    public String getLearningArea() {
        return learningArea;
    }

    public void setLearningArea(String learningArea) {
        this.learningArea = learningArea;
    }

    public String getLearningObjectives() {
        return learningObjectives;
    }

    public void setLearningObjectives(String learningObjectives) {
        this.learningObjectives = learningObjectives;
    }

    public String getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(String prerequisites) {
        this.prerequisites = prerequisites;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getModuleStatus() {
        return moduleStatus;
    }

    public void setModuleStatus(String moduleStatus) {
        this.moduleStatus = moduleStatus;
    }

    public void setStatus(String status) {
        this.moduleStatus = status;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public String getContents() {
        return contents;
    }

    public void setContents(String contents) {
        this.contents = contents;
    }

    public String getLiterature() {
        return literature;
    }

    public void setLiterature(String literature) {
        this.literature = literature;
    }

    public String getAdditionalNotes() {
        return additionalNotes;
    }

    public void setAdditionalNotes(String additionalNotes) {
        this.additionalNotes = additionalNotes;
    }

    public BigDecimal getModuleWeight() {
        return moduleWeight;
    }

    public void setModuleWeight(BigDecimal moduleWeight) {
        this.moduleWeight = moduleWeight;
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

    public String getExaminationForm() {
        return examinationForm;
    }

    public void setExaminationForm(String examinationForm) {
        this.examinationForm = examinationForm;
    }

    public Long getCreatedChangeSetId() {
        return createdChangeSetId;
    }

    public void setCreatedChangeSetId(Long createdChangeSetId) {
        this.createdChangeSetId = createdChangeSetId;
    }

}
