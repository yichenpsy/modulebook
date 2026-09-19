package de.bht.modulebook.modulebook.dto;

import java.time.LocalDate;

public record ModulebookTagRequest(
        String tagMessage,
        LocalDate modulebookReleaseDate,
        LocalDate studyExaminationRegulationsDate
) {
}
