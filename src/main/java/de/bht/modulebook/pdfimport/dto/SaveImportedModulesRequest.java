package de.bht.modulebook.pdfimport.dto;

import java.time.LocalDate;
import java.util.List;

import tools.jackson.databind.JsonNode;

// accept the normalized import shape directly first.
public record SaveImportedModulesRequest(
        Long modulebookId,
        LocalDate modulebookReleaseDate,
        LocalDate studyExaminationRegulationsDate,
        List<JsonNode> modules
) {
}
