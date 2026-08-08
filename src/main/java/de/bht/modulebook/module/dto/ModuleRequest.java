package de.bht.modulebook.module.dto;

import java.math.BigDecimal;

public record ModuleRequest(
    String code,
    String name,
    BigDecimal credits,
    String description
){
}
