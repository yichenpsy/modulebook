package de.bht.modulebook.modulebook;

import java.util.List;

import de.bht.modulebook.module.dto.ModuleResponse;
import de.bht.modulebook.studyprogram.dto.StudyProgramResponse;

public record ModulebookSnapshot(
        StudyProgramResponse studyProgram,
        List<ModuleResponse> modules
) {
}

