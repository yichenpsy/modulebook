package de.bht.modulebook.module.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ModuleResponse(
    Long id,
    String code,
    String name,
    BigDecimal credits,
    String description,
    Instant createdAt,
    Instant updatedAt
) {    
}
