package de.bht.modulebook.module.version;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleRepository;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.teachingform.ModuleTeachingFormRepository;
import de.bht.modulebook.module.workload.ModuleWorkloadRepository;
import de.bht.modulebook.teachingform.TeachingForm;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ModuleVersionService {
    private final ModuleVersionRepository versionRepository;
    private final ModuleRepository moduleRepository;
    private final ModuleWorkloadRepository workloadRepository;
    private final ModuleTeachingFormRepository teachingFormRepository;
    private final JsonMapper jsonMapper;

    public ModuleVersionService(
            ModuleVersionRepository versionRepository,
            ModuleRepository moduleRepository,
            ModuleWorkloadRepository workloadRepository,
            ModuleTeachingFormRepository teachingFormRepository,
            JsonMapper jsonMapper
    ) {
        this.versionRepository = versionRepository;
        this.moduleRepository = moduleRepository;
        this.workloadRepository = workloadRepository;
        this.teachingFormRepository = teachingFormRepository;
        this.jsonMapper = jsonMapper;
    }

    public void saveSnapshot(Long moduleId, String reason) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
        List<Long> teachingFormIds = teachingFormRepository.findByModuleId(moduleId)
                .stream()
                .map(TeachingForm::getId)
                .toList();
        ModuleSnapshot snapshot = new ModuleSnapshot(
                module,
                workloadRepository.findByModuleId(moduleId).orElse(null),
                teachingFormIds
        );

        try {
            int versionNumber = versionRepository.nextVersionNumber(moduleId);
            String snapshotJson = jsonMapper.writeValueAsString(snapshot);
            versionRepository.insert(moduleId, versionNumber, snapshotJson, reason);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create module snapshot");
        }
    }

    @Transactional
    public List<ModuleVersion> getHistory(Long moduleId) {
        List<ModuleVersion> history = versionRepository.findByModuleId(moduleId);
        if (history.isEmpty()) {
            saveSnapshot(moduleId, initialVersionReasonForExistingModule(moduleId));
            return versionRepository.findByModuleId(moduleId);
        }

        if (repairInitialVersionReason(moduleId, history)) {
            return versionRepository.findByModuleId(moduleId);
        }

        return history;
    }

    private boolean repairInitialVersionReason(Long moduleId, List<ModuleVersion> history) {
        ModuleVersion firstVersion = history.stream()
                .filter(version -> version.getVersionNumber() != null && version.getVersionNumber() == 1)
                .findFirst()
                .orElse(null);

        if (firstVersion == null || !"initial version".equals(firstVersion.getReason())) {
            return false;
        }

        versionRepository.updateReason(moduleId, 1, initialVersionReasonForExistingModule(moduleId));
        return true;
    }

    private String initialVersionReasonForExistingModule(Long moduleId) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));

        if (looksLikePdfImportedModule(module)) {
            return ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON;
        }

        return ModuleService.MANUAL_INITIAL_VERSION_REASON;
    }

    private boolean looksLikePdfImportedModule(Module module) {
        String description = module.getDescription();
        if (description == null) {
            return false;
        }

        return description.contains("Source file:") && description.contains("Source pages:");
    }

    @Transactional
    public Module restore(Long moduleId, int versionNumber) {
        ModuleVersion version = versionRepository.findByModuleIdAndVersionNumber(moduleId, versionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));

        saveSnapshot(moduleId, "before restore to version " + versionNumber);

        try {
            ModuleSnapshot snapshot = jsonMapper.readValue(version.getSnapshotJson(), ModuleSnapshot.class);
            boolean updated = moduleRepository.update(moduleId, snapshot.getModule());

            if (!updated) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
            }
            if (snapshot.getWorkload() == null) {
                workloadRepository.deleteByModuleId(moduleId);
            } else {
                workloadRepository.upsert(moduleId, snapshot.getWorkload());
            }
            teachingFormRepository.replaceForModule(moduleId, snapshot.getTeachingFormIds());

            return moduleRepository.findById(moduleId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not restore module snapshot");
        }
    }
}
