package de.bht.modulebook.modulebook.dto;

import java.time.LocalDate;

public record ModulebookVersionRequest(
        String versionMessage,
        LocalDate modulebookReleaseDate,
        LocalDate studyExaminationRegulationsDate
) {
}
