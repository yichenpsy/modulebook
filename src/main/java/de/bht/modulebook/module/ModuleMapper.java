package de.bht.modulebook.module;

import de.bht.modulebook.module.dto.ModuleRequest;
import de.bht.modulebook.module.dto.ModuleResponse;

public class ModuleMapper {
    // takes JSON request data and builds a Module object the service can save
    public static Module toEntity(ModuleRequest request) {
        Module module = new Module();
        module.setStudyProgramId(request.studyProgramId());
        module.setCode(request.code());
        module.setName(request.name());
        module.setCredits(request.credits());
        module.setDescription(request.description());
        return module;
    }

    // takes the saved Module and builds the response object for the API.
    public static ModuleResponse toResponse(Module module) {
        return new ModuleResponse(
            module.getId(),
            module.getStudyProgramId(),
            module.getCode(),
            module.getName(),
            module.getCredits(),
            module.getDescription(),
            module.getCreatedAt(),
            module.getUpdatedAt()            
        );
    }
}
