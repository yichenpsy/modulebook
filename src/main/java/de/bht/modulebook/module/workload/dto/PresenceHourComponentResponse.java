package de.bht.modulebook.module.workload.dto;

import java.math.BigDecimal;

public record PresenceHourComponentResponse(
    Long id,
    Long teachingFormId,
    String teachingFormCode,
    BigDecimal swsAmount
) {
}