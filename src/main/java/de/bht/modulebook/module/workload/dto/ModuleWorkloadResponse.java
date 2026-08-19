package de.bht.modulebook.module.workload.dto;

import java.math.BigDecimal;
import java.util.List;

public record ModuleWorkloadResponse(
    Long id,
    BigDecimal presenceHours,
    BigDecimal selfStudyHours,
    String rawText,
    List<PresenceHourComponentResponse> components
) {
}