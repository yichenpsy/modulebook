package de.bht.modulebook.module.version.dto;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.version.ModuleVersion;

public record StudyProgramModuleHistoryResponse(
        Module module,
        ModuleVersion version,
        boolean moduleDeleted
) {
}
