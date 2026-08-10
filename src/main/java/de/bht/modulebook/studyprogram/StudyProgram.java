package de.bht.modulebook.studyprogram;

import java.time.Instant;

public class StudyProgram {

    private Long id;
    private String name;
    private Integer year;
    private Instant createdAt;
    private Instant updatedAt;

    public StudyProgram() {
    }

    public StudyProgram(Long id, String name, Integer year, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.year = year;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
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
