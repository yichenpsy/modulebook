package de.bht.modulebook.studyprogram.changeset;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyProgramChangeSetService {
    public static final String SOURCE_MANUAL_EDIT = "MANUAL_EDIT";
    public static final String SOURCE_PDF_IMPORT = "PDF_IMPORT";
    public static final String SOURCE_RESTORE = "RESTORE";
    public static final String SOURCE_RESTORE_BEFORE = "RESTORE_BEFORE";
    public static final String SOURCE_RESTORE_TAG = "RESTORE_TAG";
    public static final String SOURCE_DELETE = "DELETE";
    public static final String SOURCE_INITIAL_MODULE = "INITIAL_MODULE";

    private final StudyProgramChangeSetRepository repository;

    public StudyProgramChangeSetService(StudyProgramChangeSetRepository repository) {
        this.repository = repository;
    }

    public StudyProgramChangeSet create(Long studyProgramId, String sourceType, String reason) {
        if (studyProgramId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study program id is required");
        }
        if (sourceType == null || sourceType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change set source type is required");
        }
        return repository.insert(studyProgramId, sourceType.trim(), normalizeReason(reason));
    }

    public List<StudyProgramChangeSet> getByStudyProgramId(Long studyProgramId) {
        if (studyProgramId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study program id is required");
        }
        return repository.findByStudyProgramId(studyProgramId);
    }

    public boolean isFirstSourceChangeSet(Long studyProgramId, String sourceType, Long changeSetId) {
        if (studyProgramId == null || sourceType == null || changeSetId == null) {
            return false;
        }

        return repository.findFirstByStudyProgramIdAndSourceType(studyProgramId, sourceType)
                .map(changeSet -> changeSetId.equals(changeSet.getId()))
                .orElse(false);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
