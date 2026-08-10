package de.bht.modulebook.pdfimport.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;

// accept the normalized import shape directly first.
public record SaveImportedModulesRequest(
        String studyProgram,
        Integer year,
        List<JsonNode> modules
) {
}