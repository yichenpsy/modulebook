package de.bht.modulebook.modulebook.changeset;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModulebookChangeSetService {
    public static final String SOURCE_MANUAL_EDIT = "MANUAL_EDIT";
    public static final String SOURCE_PDF_IMPORT = "PDF_IMPORT";
    public static final String SOURCE_RESTORE = "RESTORE";
    public static final String SOURCE_RESTORE_BEFORE = "RESTORE_BEFORE";
    public static final String SOURCE_RESTORE_TAG = "RESTORE_TAG";
    public static final String SOURCE_DELETE = "DELETE";
    public static final String SOURCE_INITIAL_MODULE = "INITIAL_MODULE";

    private final ModulebookChangeSetRepository repository;

    public ModulebookChangeSetService(ModulebookChangeSetRepository repository) {
        this.repository = repository;
    }

    public ModulebookChangeSet create(Long modulebookId, String sourceType, String reason) {
        if (modulebookId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modulebook id is required");
        }
        if (sourceType == null || sourceType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change set source type is required");
        }
        return repository.insert(modulebookId, sourceType.trim(), normalizeReason(reason));
    }

    public List<ModulebookChangeSet> getByModulebookId(Long modulebookId) {
        if (modulebookId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modulebook id is required");
        }
        return repository.findByModulebookId(modulebookId);
    }

    public boolean isFirstSourceChangeSet(Long modulebookId, String sourceType, Long changeSetId) {
        if (modulebookId == null || sourceType == null || changeSetId == null) {
            return false;
        }

        return repository.findFirstByModulebookIdAndSourceType(modulebookId, sourceType)
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
