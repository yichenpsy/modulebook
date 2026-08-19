package de.bht.modulebook.module.workload.dto;

import java.math.BigDecimal;

public record PresenceHourComponentRequest(
    Long teachingFormId,
    BigDecimal swsAmount
) {
}