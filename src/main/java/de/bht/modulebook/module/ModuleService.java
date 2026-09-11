package de.bht.modulebook.module;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.module.workload.ModuleWorkloadRepository;
import de.bht.modulebook.module.workload.PresenceHourComponent;
import de.bht.modulebook.module.teachingform.ModuleTeachingFormRepository;
import de.bht.modulebook.module.version.ModuleVersionService;
import de.bht.modulebook.teachingform.TeachingForm;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;

// logic layer, checkes things like "module not found", calls repository
@Service
public class ModuleService {
    private static final BigDecimal WEEKS_PER_SEMESTER = BigDecimal.valueOf(17);
    private static final BigDecimal HOURS_PER_CREDIT = BigDecimal.valueOf(30);
    public static final String ACTIVE_STATUS = "ACTIVE";
    public static final String DELETED_STATUS = "DELETED";
    public static final String MANUAL_INITIAL_VERSION_REASON = "created manually";
    public static final String PDF_IMPORT_INITIAL_VERSION_REASON = "created from PDF import";

    private final ModuleRepository moduleRepository;
    private final ModuleWorkloadRepository workloadRepository;
    private final ModuleTeachingFormRepository moduleTeachingFormRepository;
    private final ModuleVersionService versionService;
    private final StudyProgramChangeSetService changeSetService;
    

    public ModuleService(
        ModuleRepository moduleRepository,
        ModuleWorkloadRepository workloadRepository,
        ModuleTeachingFormRepository moduleTeachingFormRepository,
        ModuleVersionService versionService,
        StudyProgramChangeSetService changeSetService
    ) {
        // The service needs the repository to access the database.
        this.moduleRepository = moduleRepository;
        this.workloadRepository = workloadRepository;
        this.moduleTeachingFormRepository = moduleTeachingFormRepository;
        this.versionService = versionService;
        this.changeSetService = changeSetService;
    }

    public Module create(Module module) {
        return create(module, null, List.of(), MANUAL_INITIAL_VERSION_REASON);
    }

    public Module create(Module module, ModuleWorkload workload) {
        return create(module, workload, List.of(), MANUAL_INITIAL_VERSION_REASON);
    }

    @Transactional
    public Module create(Module module, ModuleWorkload workload, List<Long> teachingFormIds) {
        return create(module, workload, teachingFormIds, MANUAL_INITIAL_VERSION_REASON);
    }

    @Transactional
    public Module createFromPdfImport(Module module) {
        return create(module, null, List.of(), PDF_IMPORT_INITIAL_VERSION_REASON);
    }

    @Transactional
    public Module createFromPdfImport(Module module, Long changeSetId) {
        return create(module, null, List.of(), PDF_IMPORT_INITIAL_VERSION_REASON, changeSetId);
    }

    @Transactional
    public Module create(
            Module module,
            ModuleWorkload workload,
            List<Long> teachingFormIds,
            String initialVersionReason
    ) {
        String normalizedReason = normalizeInitialVersionReason(initialVersionReason);
        Long changeSetId = createChangeSet(module.getStudyProgramId(), normalizedReason, StudyProgramChangeSetService.SOURCE_INITIAL_MODULE);
        return create(module, workload, teachingFormIds, normalizedReason, changeSetId);
    }

    @Transactional
    public Module create(
            Module module,
            ModuleWorkload workload,
            List<Long> teachingFormIds,
            String initialVersionReason,
            Long changeSetId
    ) {
        applyDefaultStatus(module);
        Module created = moduleRepository.insert(module);
        if (workload != null) {
            calculateWorkload(created, workload);
            workloadRepository.upsert(created.getId(), workload);
        }
        moduleTeachingFormRepository.replaceForModule(
                created.getId(),
                mergeTeachingFormIds(teachingFormIds, workload)
        );
        versionService.saveSnapshot(created.getId(), normalizeInitialVersionReason(initialVersionReason), changeSetId);
        return getById(created.getId());
    }

    private void applyDefaultStatus(Module module) {
        if (module.getModuleStatus() == null || module.getModuleStatus().isBlank()) {
            module.setModuleStatus(ACTIVE_STATUS);
        }
    }

    public Module getById(Long id) {
        return moduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
    }

    public Optional<Module> findByStudyProgramIdAndModuleNumber(Long studyProgramId, String moduleNumber) {
        return moduleRepository.findByStudyProgramIdAndModuleNumber(studyProgramId, moduleNumber);
    }

    public List<Module> getAll() {
        return moduleRepository.findAll();
    }

    public List<Module> getByStudyProgramId(Long studyProgramId) {
        return moduleRepository.findByStudyProgramId(studyProgramId);
    }
      
    public Module update(Long id, Module module) {
        versionService.saveSnapshotInNewChangeSet(id, "before update", StudyProgramChangeSetService.SOURCE_MANUAL_EDIT);
        boolean updated = moduleRepository.update(id, module);

        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }

        return getById(id);
    }

    @Transactional
    public Module update(Long id, Module module, String versionReason) {
        versionService.saveSnapshotInNewChangeSet(id, normalizeVersionReason(versionReason), StudyProgramChangeSetService.SOURCE_MANUAL_EDIT);
        boolean updated = moduleRepository.update(id, module);

        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }

        return getById(id);
    }

    @Transactional
    public Module update(Long id, Module module, String versionReason, Long changeSetId) {
        versionService.saveSnapshot(id, normalizeVersionReason(versionReason), changeSetId);
        boolean updated = moduleRepository.update(id, module);

        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }

        return getById(id);
    }

    public Module update(Long id, Module module, ModuleWorkload workload) {
        List<Long> existingTeachingFormIds = moduleTeachingFormRepository.findByModuleId(id)
                .stream()
                .map(TeachingForm::getId)
                .toList();
        return update(id, module, workload, existingTeachingFormIds);
    }

    @Transactional
    public Module update(Long id, Module module, ModuleWorkload workload, List<Long> teachingFormIds){
        return update(id, module, workload, teachingFormIds, null);
    }

    @Transactional
    public Module update(Long id, Module module, ModuleWorkload workload, List<Long> teachingFormIds, String versionReason){
        versionService.saveSnapshotInNewChangeSet(id, normalizeVersionReason(versionReason), StudyProgramChangeSetService.SOURCE_MANUAL_EDIT);
        return updateAfterSnapshot(id, module, workload, teachingFormIds);
    }

    @Transactional
    public Module update(Long id, Module module, ModuleWorkload workload, List<Long> teachingFormIds, String versionReason, Long changeSetId){
        versionService.saveSnapshot(id, normalizeVersionReason(versionReason), changeSetId);
        return updateAfterSnapshot(id, module, workload, teachingFormIds);
    }

    private Module updateAfterSnapshot(Long id, Module module, ModuleWorkload workload, List<Long> teachingFormIds) {
        boolean updated = moduleRepository.update(id, module);

        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }

        if (workload != null) {
           calculateWorkload(module, workload);
           workloadRepository.upsert(id, workload); 
        }
        moduleTeachingFormRepository.replaceForModule(
                id,
                mergeTeachingFormIds(teachingFormIds, workload)
        );

        return getById(id);
    }


    @Transactional
    public void delete(Long id) {
        versionService.saveSnapshotInNewChangeSet(id, "before delete", StudyProgramChangeSetService.SOURCE_DELETE);
        boolean deleted = moduleRepository.updateModuleStatus(id, DELETED_STATUS);

        // The repository returns false when no row with this id exists.
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }
    }

    public Optional<ModuleWorkload> getWorkloadByModuleId(Long moduleId) {
        return workloadRepository.findByModuleId(moduleId);
    }

    public List<TeachingForm> getTeachingFormsByModuleId(Long moduleId) {
        return moduleTeachingFormRepository.findByModuleId(moduleId);
    }

    private void calculateWorkload(Module module, ModuleWorkload workload) {
        BigDecimal totalPresenceHours = workload.getComponents().stream()
                .filter(component -> component.getSwsAmount() != null)
                .map(component -> component.getSwsAmount().multiply(WEEKS_PER_SEMESTER))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWorkloadHours = module.getCredits().multiply(HOURS_PER_CREDIT);
        BigDecimal selfStudyHours = totalWorkloadHours.subtract(totalPresenceHours);

        workload.setPresenceHours(totalPresenceHours);
        workload.setSelfStudyHours(selfStudyHours);
    }

    private List<Long> mergeTeachingFormIds(List<Long> teachingFormIds, ModuleWorkload workload) {
        Set<Long> mergedIds = new LinkedHashSet<>();
        if (teachingFormIds != null) {
            mergedIds.addAll(teachingFormIds);
        }
        if (workload != null) {
            workload.getComponents().stream()
                    .map(PresenceHourComponent::getTeachingFormId)
                    .filter(teachingFormId -> teachingFormId != null)
                    .forEach(mergedIds::add);
        }
        return new ArrayList<>(mergedIds);
    }

    private String normalizeVersionReason(String versionReason) {
        if (versionReason == null || versionReason.isBlank()) {
            return "before update";
        }
        return versionReason.trim();
    }

    private String normalizeInitialVersionReason(String initialVersionReason) {
        if (initialVersionReason == null || initialVersionReason.isBlank()) {
            return MANUAL_INITIAL_VERSION_REASON;
        }
        return initialVersionReason.trim();
    }

    private Long createChangeSet(Long studyProgramId, String reason, String sourceType) {
        return changeSetService.create(studyProgramId, sourceType, reason).getId();
    }
}
