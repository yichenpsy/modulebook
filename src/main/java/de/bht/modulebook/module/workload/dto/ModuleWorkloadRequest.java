package de.bht.modulebook.module.workload.dto;

import java.math.BigDecimal;
import java.util.List;

public record ModuleWorkloadRequest(
    List<PresenceHourComponentRequest> components,
    BigDecimal presenceHours,
    BigDecimal selfStudyHours,
    String rawText
) {
}
