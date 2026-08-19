package de.bht.modulebook.teachingform;

public class TeachingForm {
    private Long id;
    private String code;
    private String labelDe;
    private String labelEn;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabelDe() {
        return labelDe;
    }

    public void setLabelDe(String labelDe) {
        this.labelDe = labelDe;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public void setLabelEn(String labelEn) {
        this.labelEn = labelEn;
    }
}
