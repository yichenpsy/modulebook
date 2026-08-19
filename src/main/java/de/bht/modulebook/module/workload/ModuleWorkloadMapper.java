package de.bht.modulebook.module.workload;

import java.util.List;

import de.bht.modulebook.module.workload.dto.ModuleWorkloadRequest;
import de.bht.modulebook.module.workload.dto.ModuleWorkloadResponse;
import de.bht.modulebook.module.workload.dto.PresenceHourComponentResponse;

public class ModuleWorkloadMapper {
    public static ModuleWorkload toEntity(ModuleWorkloadRequest request) {
        if (request == null) {
            return null;
        }

        ModuleWorkload workload = new ModuleWorkload();
        workload.setRawText(request.rawText());
        workload.setComponents(
                request.components() == null
                        ? List.of()
                        : request.components().stream()
                                .map(componentRequest -> {
                                    PresenceHourComponent component = new PresenceHourComponent();
                                    component.setTeachingFormId(componentRequest.teachingFormId());
                                    component.setSwsAmount(componentRequest.swsAmount());
                                    return component;
                                })
                                .toList()
        );
        return workload;
    }

    public static ModuleWorkloadResponse toResponse(ModuleWorkload workload) {
        if (workload == null) {
            return null;
        }

        return new ModuleWorkloadResponse(
                workload.getId(),
                workload.getPresenceHours(),
                workload.getSelfStudyHours(),
                workload.getRawText(),
                workload.getComponents().stream()
                        .map(component -> new PresenceHourComponentResponse(
                                component.getId(),
                                component.getTeachingFormId(),
                                component.getTeachingFormCode(),
                                component.getSwsAmount()
                        ))
                        .toList()
        );
    }
}
