package de.bht.modulebook.module.workload;

import java.math.BigDecimal;

public class PresenceHourComponent {
    private Long id;
    private Long teachingFormId;
    private String teachingFormCode;
    private BigDecimal swsAmount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTeachingFormId() {
        return teachingFormId;
    }

    public void setTeachingFormId(Long teachingFormId) {
        this.teachingFormId = teachingFormId;
    }

    public String getTeachingFormCode() {
        return teachingFormCode;
    }

    public void setTeachingFormCode(String teachingFormCode) {
        this.teachingFormCode = teachingFormCode;
    }

    public BigDecimal getSwsAmount() {
        return swsAmount;
    }

    public void setSwsAmount(BigDecimal swsAmount) {
        this.swsAmount = swsAmount;
    }
}
