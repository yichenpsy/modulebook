package de.bht.modulebook.module.workload.dto;

import java.util.List;

public record ModuleWorkloadRequest(
    List<PresenceHourComponentRequest> components,
    String rawText
) {
}