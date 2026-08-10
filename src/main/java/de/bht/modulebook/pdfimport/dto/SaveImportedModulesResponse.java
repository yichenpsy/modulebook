package de.bht.modulebook.pdfimport.dto;

import java.util.List;

import de.bht.modulebook.module.dto.ModuleResponse;

// frontend needs to know what was saved and what failed.
public record SaveImportedModulesResponse(
        int requestedCount,
        int createdCount,
        int errorCount,
        List<ModuleResponse> createdModules,
        List<SaveImportedModuleError> errors
) {
}