package de.bht.modulebook.modulebook;

import java.util.List;

import de.bht.modulebook.module.dto.ModuleResponse;
import de.bht.modulebook.modulebook.dto.ModulebookResponse;

public record ModulebookSnapshot(
        ModulebookResponse modulebook,
        List<ModuleResponse> modules
) {
}
