package de.bht.modulebook.module.dto;

import java.math.BigDecimal;

public record ModuleRequest(
    Long studyProgramId,
    String code,
    String name,
    BigDecimal credits,
    String description
){
}
