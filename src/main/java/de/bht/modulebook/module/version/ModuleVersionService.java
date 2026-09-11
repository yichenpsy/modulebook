package de.bht.modulebook.module.version;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleRepository;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.version.dto.StudyProgramModuleHistoryResponse;
import de.bht.modulebook.module.teachingform.ModuleTeachingFormRepository;
import de.bht.modulebook.module.workload.ModuleWorkloadRepository;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSet;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;
import de.bht.modulebook.teachingform.TeachingForm;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ModuleVersionService {
    private final ModuleVersionRepository versionRepository;
    private final ModuleRepository moduleRepository;
    private final ModuleWorkloadRepository workloadRepository;
    private final ModuleTeachingFormRepository teachingFormRepository;
    private final StudyProgramChangeSetService changeSetService;
    private final JsonMapper jsonMapper;

    public ModuleVersionService(
            ModuleVersionRepository versionRepository,
            ModuleRepository moduleRepository,
            ModuleWorkloadRepository workloadRepository,
            ModuleTeachingFormRepository teachingFormRepository,
            StudyProgramChangeSetService changeSetService,
            JsonMapper jsonMapper
    ) {
        this.versionRepository = versionRepository;
        this.moduleRepository = moduleRepository;
        this.workloadRepository = workloadRepository;
        this.teachingFormRepository = teachingFormRepository;
        this.changeSetService = changeSetService;
        this.jsonMapper = jsonMapper;
    }

    public void saveSnapshot(Long moduleId, String reason) {
        saveSnapshot(moduleId, reason, null);
    }

    public void saveSnapshot(Long moduleId, String reason, Long changeSetId) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
        saveSnapshot(module, reason, changeSetId);
    }

    public StudyProgramChangeSet saveSnapshotInNewChangeSet(Long moduleId, String reason, String sourceType) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
        StudyProgramChangeSet changeSet = changeSetService.create(module.getStudyProgramId(), sourceType, reason);
        saveSnapshot(module, reason, changeSet.getId());
        return changeSet;
    }

    private void saveSnapshot(Module module, String reason, Long changeSetId) {
        Long moduleId = module.getId();
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
            versionRepository.insert(moduleId, module.getStudyProgramId(), changeSetId, versionNumber, snapshotJson, reason);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create module snapshot");
        }
    }

    public List<StudyProgramModuleHistoryResponse> getStudyProgramHistory(Long studyProgramId) {
        return versionRepository.findByStudyProgramId(studyProgramId)
                .stream()
                .map(this::toStudyProgramHistoryResponse)
                .filter(row -> row.module() != null
                        && Objects.equals(row.module().getStudyProgramId(), studyProgramId))
                .toList();
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

    private StudyProgramModuleHistoryResponse toStudyProgramHistoryResponse(ModuleVersion version) {
        Module currentModule = version.getModuleId() == null
                ? null
                : moduleRepository.findById(version.getModuleId()).orElse(null);
        Module moduleForDisplay = currentModule == null ? moduleFromSnapshot(version) : currentModule;

        return new StudyProgramModuleHistoryResponse(
                moduleForDisplay,
                version,
                currentModule == null
        );
    }

    private Module moduleFromSnapshot(ModuleVersion version) {
        try {
            return jsonMapper.readValue(version.getSnapshotJson(), ModuleSnapshot.class).getModule();
        } catch (JacksonException e) {
            return null;
        }
    }

    @Transactional
    public Module restore(Long moduleId, int versionNumber) {
        ModuleVersion version = versionRepository.findByModuleIdAndVersionNumber(moduleId, versionNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));

        saveSnapshotInNewChangeSet(
                moduleId,
                "before restore to version " + versionNumber,
                StudyProgramChangeSetService.SOURCE_RESTORE
        );

        try {
            ModuleSnapshot snapshot = jsonMapper.readValue(version.getSnapshotJson(), ModuleSnapshot.class);
            Module module = restorableModule(snapshot);
            boolean updated = moduleRepository.update(moduleId, module);

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

    private Module restorableModule(ModuleSnapshot snapshot) {
        Module module = snapshot.getModule();
        if (module == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version does not contain a restorable module");
        }

        if (ModuleService.DELETED_STATUS.equals(module.getModuleStatus())) {
            module.setModuleStatus(ModuleService.ACTIVE_STATUS);
        }

        return module;
    }

    @Transactional
    public Module restoreDeleted(Long versionId) {
        ModuleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));

        try {
            ModuleSnapshot snapshot = jsonMapper.readValue(version.getSnapshotJson(), ModuleSnapshot.class);
            Module module = restorableModule(snapshot);
            if (version.getStudyProgramId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version does not contain a restorable module");
            }

            Long oldModuleId = module.getId();
            module.setStudyProgramId(version.getStudyProgramId());

            StudyProgramChangeSet changeSet = changeSetService.create(
                    version.getStudyProgramId(),
                    StudyProgramChangeSetService.SOURCE_RESTORE,
                    "restore deleted module from version " + version.getVersionNumber()
            );

            Module restored = moduleRepository.insert(module);
            if (snapshot.getWorkload() != null) {
                workloadRepository.upsert(restored.getId(), snapshot.getWorkload());
            }
            teachingFormRepository.replaceForModule(restored.getId(), snapshot.getTeachingFormIds());
            if (oldModuleId != null) {
                versionRepository.relinkDeletedModuleHistory(version.getStudyProgramId(), oldModuleId, restored.getId());
            }
            saveSnapshot(restored.getId(), "restored deleted module from version " + version.getVersionNumber(), changeSet.getId());

            return moduleRepository.findById(restored.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not restore deleted module snapshot");
        }
    }
}
