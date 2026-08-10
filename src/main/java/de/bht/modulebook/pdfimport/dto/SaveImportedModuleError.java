package de.bht.modulebook.pdfimport.dto;

// save import errors
public record SaveImportedModuleError(
        int index,
        String moduleNumber,
        String message
) {
}