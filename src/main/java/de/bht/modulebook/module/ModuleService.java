package de.bht.modulebook.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
import de.bht.modulebook.module.version.ModuleChangeLogService;
import de.bht.modulebook.modulebook.ModulebookService;
import de.bht.modulebook.teachingform.TeachingForm;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;

// logic layer, checkes things like "module not found", calls repository
@Service
public class ModuleService {
    public static final String ACTIVE_STATUS = "ACTIVE";
    public static final String DEACTIVE_STATUS = "DEACTIVE";
    public static final String DELETED_STATUS = "DELETED";
    public static final String MANUAL_INITIAL_VERSION_REASON = "created manually";
    public static final String PDF_IMPORT_INITIAL_VERSION_REASON = "created from PDF import";

    private final ModuleRepository moduleRepository;
    private final ModuleWorkloadRepository workloadRepository;
    private final ModuleTeachingFormRepository moduleTeachingFormRepository;
    private final StudyProgramChangeSetService changeSetService;
    private final ModuleChangeLogService changeLogService;
    private final ModulebookService modulebookService;
    

    public ModuleService(
        ModuleRepository moduleRepository,
        ModuleWorkloadRepository workloadRepository,
        ModuleTeachingFormRepository moduleTeachingFormRepository,
        StudyProgramChangeSetService changeSetService,
        ModuleChangeLogService changeLogService,
        ModulebookService modulebookService
    ) {
        // The service needs the repository to access the database.
        this.moduleRepository = moduleRepository;
        this.workloadRepository = workloadRepository;
        this.moduleTeachingFormRepository = moduleTeachingFormRepository;
        this.changeSetService = changeSetService;
        this.changeLogService = changeLogService;
        this.modulebookService = modulebookService;
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
        normalizeModulebookReference(module);
        normalizeAndValidateUniqueModuleNumberForCreate(module);
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
        normalizeModulebookReference(module);
        normalizeAndValidateUniqueModuleNumberForCreate(module);
        ensureModulebookId(module);
        applyDefaultStatus(module);
        module.setCreatedChangeSetId(changeSetId);
        Module created = moduleRepository.insert(module);
        if (workload != null) {
            workloadRepository.upsert(created.getId(), workload);
        }
        moduleTeachingFormRepository.replaceForModule(
                created.getId(),
                mergeTeachingFormIds(teachingFormIds, workload)
        );
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
        return findExistingModuleNumber(studyProgramId, normalizeModuleNumber(moduleNumber));
    }

    public List<Module> getAll() {
        return moduleRepository.findAll();
    }

    public List<Module> getByStudyProgramId(Long studyProgramId) {
        return moduleRepository.findByStudyProgramId(studyProgramId);
    }

    @Transactional
    public Module updateModuleStatus(Long id, String targetStatus) {
        String normalizedStatus = normalizeActiveStatus(targetStatus);
        Module oldModule = getById(id);
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(id);
        var changeSet = changeSetService.create(
            oldModule.getStudyProgramId(),
            StudyProgramChangeSetService.SOURCE_MANUAL_EDIT,
            "changed module status to " + normalizedStatus
        );
        Module desiredModule = copyModule(oldModule);
        desiredModule.setModuleStatus(normalizedStatus);
        return updateAndLogSavedState(
                id,
                desiredModule,
                oldWorkload,
                oldTeachingFormIds,
                changeSet.getId(),
                oldModule,
                oldWorkload,
                oldTeachingFormIds,
                List.of("module.moduleStatus")
        );
    }
      
    public Module update(Long id, Module module) {
        Module oldModule = getById(id);
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(id);
        normalizeAndValidateUniqueModuleNumberForUpdate(id, module, oldModule);
        ensureModulebookId(module, oldModule);
        var changeSet = changeSetService.create(
            oldModule.getStudyProgramId(),
            StudyProgramChangeSetService.SOURCE_MANUAL_EDIT,
            "update"
        );
        boolean updated = moduleRepository.update(id, module);

        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }

        Module savedModule = getById(id);
        ModuleWorkload savedWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> savedTeachingFormIds = teachingFormIdsByModuleId(id);
        changeLogService.saveChanges(
                changeSet.getId(),
                id,
                oldModule,
                savedModule,
                oldWorkload,
                savedWorkload,
                oldTeachingFormIds,
                savedTeachingFormIds
        );

        return savedModule;
    }

    @Transactional
    public Module update(Long id, Module module, String versionReason) {
        Module oldModule = getById(id);
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(id);
        normalizeAndValidateUniqueModuleNumberForUpdate(id, module, oldModule);
        ensureModulebookId(module, oldModule);
        var changeSet = changeSetService.create(
            oldModule.getStudyProgramId(),
            StudyProgramChangeSetService.SOURCE_MANUAL_EDIT,
            normalizeVersionReason(versionReason)
        );
        boolean updated = moduleRepository.update(id, module);

        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }

        Module savedModule = getById(id);
        ModuleWorkload savedWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> savedTeachingFormIds = teachingFormIdsByModuleId(id);
        changeLogService.saveChanges(
                changeSet.getId(),
                id,
                oldModule,
                savedModule,
                oldWorkload,
                savedWorkload,
                oldTeachingFormIds,
                savedTeachingFormIds
        );

        return savedModule;
    }

    @Transactional
    public Module update(Long id, Module module, String versionReason, Long changeSetId) {
        Module oldModule = getById(id);
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(id);
        normalizeAndValidateUniqueModuleNumberForUpdate(id, module, oldModule);
        ensureModulebookId(module, oldModule);
        boolean updated = moduleRepository.update(id, module);

        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }

        Module savedModule = getById(id);
        ModuleWorkload savedWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> savedTeachingFormIds = teachingFormIdsByModuleId(id);
        changeLogService.saveChanges(
                changeSetId,
                id,
                oldModule,
                savedModule,
                oldWorkload,
                savedWorkload,
                oldTeachingFormIds,
                savedTeachingFormIds
        );

        return savedModule;
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
        return update(id, module, workload, teachingFormIds, versionReason, (List<String>) null);
    }

    @Transactional
    public Module update(Long id, Module module, ModuleWorkload workload, List<Long> teachingFormIds, String versionReason, List<String> changedFieldPaths){
        Module oldModule = getById(id);
        if (changedFieldPaths != null && changedFieldPaths.isEmpty()) {
            return oldModule;
        }
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(id);
        preserveUnchangedModuleFields(module, oldModule, changedFieldPaths);
        normalizeAndValidateUniqueModuleNumberForUpdate(id, module, oldModule);
        var changeSet = changeSetService.create(
            oldModule.getStudyProgramId(),
            StudyProgramChangeSetService.SOURCE_MANUAL_EDIT,
            normalizeVersionReason(versionReason)
        );
        return updateAndLogSavedState(
                id,
                module,
                workload,
                teachingFormIds,
                changeSet.getId(),
                oldModule,
                oldWorkload,
                oldTeachingFormIds,
                changedFieldPaths
        );
    }

    @Transactional
    public Module update(Long id, Module module, ModuleWorkload workload, List<Long> teachingFormIds, String versionReason, Long changeSetId){
        Module oldModule = getById(id);
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(id);
        return updateAndLogSavedState(
                id,
                module,
                workload,
                teachingFormIds,
                changeSetId,
                oldModule,
                oldWorkload,
                oldTeachingFormIds,
                null
        );
    }

    private Module updateAndLogSavedState(
            Long id,
            Module module,
            ModuleWorkload workload,
            List<Long> teachingFormIds,
            Long changeSetId,
            Module oldModule,
            ModuleWorkload oldWorkload,
            List<Long> oldTeachingFormIds
    ) {
        return updateAndLogSavedState(
                id,
                module,
                workload,
                teachingFormIds,
                changeSetId,
                oldModule,
                oldWorkload,
                oldTeachingFormIds,
                null
        );
    }

    private Module updateAndLogSavedState(
            Long id,
            Module module,
            ModuleWorkload workload,
            List<Long> teachingFormIds,
            Long changeSetId,
            Module oldModule,
            ModuleWorkload oldWorkload,
            List<Long> oldTeachingFormIds,
            List<String> changedFieldPaths
    ) {
        normalizeAndValidateUniqueModuleNumberForUpdate(id, module, oldModule);
        ensureModulebookId(module, oldModule);
        Module savedModule = saveModuleState(id, module, workload, teachingFormIds);
        ModuleWorkload savedWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> savedTeachingFormIds = teachingFormIdsByModuleId(id);

        if (changedFieldPaths == null) {
            changeLogService.saveChanges(
                    changeSetId,
                    id,
                    oldModule,
                    savedModule,
                    oldWorkload,
                    savedWorkload,
                    oldTeachingFormIds,
                    savedTeachingFormIds
            );
        } else {
            changeLogService.saveChanges(
                    changeSetId,
                    id,
                    oldModule,
                    savedModule,
                    oldWorkload,
                    savedWorkload,
                    oldTeachingFormIds,
                    savedTeachingFormIds,
                    changedFieldPaths
            );
        }

        return savedModule;
    }

    private void preserveUnchangedModuleFields(Module module, Module oldModule, List<String> changedFieldPaths) {
        if (changedFieldPaths == null) {
            return;
        }

        Set<String> changedPaths = new HashSet<>(changedFieldPaths);
        if (!changedPaths.contains("module.moduleNumber")) {
            module.setModuleNumber(oldModule.getModuleNumber());
        }
        if (!changedPaths.contains("module.name")) {
            module.setName(oldModule.getName());
        }
        if (!changedPaths.contains("module.credits")) {
            module.setCredits(oldModule.getCredits());
        }
        if (!changedPaths.contains("module.description")) {
            module.setDescription(oldModule.getDescription());
        }
        if (!changedPaths.contains("module.moduleType")) {
            module.setModuleType(oldModule.getModuleType());
        }
        if (!changedPaths.contains("module.titleDe")) {
            module.setTitleDe(oldModule.getTitleDe());
        }
        if (!changedPaths.contains("module.titleEn")) {
            module.setTitleEn(oldModule.getTitleEn());
        }
        if (!changedPaths.contains("module.coordinator")) {
            module.setCoordinator(oldModule.getCoordinator());
        }
        if (!changedPaths.contains("module.applicability")) {
            module.setApplicability(oldModule.getApplicability());
        }
        if (!changedPaths.contains("module.learningArea")) {
            module.setLearningArea(oldModule.getLearningArea());
        }
        if (!changedPaths.contains("module.learningObjectives")) {
            module.setLearningObjectives(oldModule.getLearningObjectives());
        }
        if (!changedPaths.contains("module.prerequisites")) {
            module.setPrerequisites(oldModule.getPrerequisites());
        }
        if (!changedPaths.contains("module.level")) {
            module.setLevel(oldModule.getLevel());
        }
        if (!changedPaths.contains("module.moduleStatus")) {
            module.setModuleStatus(oldModule.getModuleStatus());
        }
        if (!changedPaths.contains("module.moduleComments")) {
            module.setModuleComments(oldModule.getModuleComments());
        }
        if (!changedPaths.contains("module.frequency")) {
            module.setFrequency(oldModule.getFrequency());
        }
        if (!changedPaths.contains("module.examinationForm")) {
            module.setExaminationForm(oldModule.getExaminationForm());
        }
        if (!changedPaths.contains("module.contents")) {
            module.setContents(oldModule.getContents());
        }
        if (!changedPaths.contains("module.literature")) {
            module.setLiterature(oldModule.getLiterature());
        }
        if (!changedPaths.contains("module.additionalNotes")) {
            module.setAdditionalNotes(oldModule.getAdditionalNotes());
        }
        if (!changedPaths.contains("module.moduleWeight")) {
            module.setModuleWeight(oldModule.getModuleWeight());
        }
    }

    private Module saveModuleState(Long id, Module module, ModuleWorkload workload, List<Long> teachingFormIds) {
        boolean updated = moduleRepository.update(id, module);

        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }

        if (workload != null) {
           workloadRepository.upsert(id, workload); 
        }
        if (workload != null || teachingFormIds != null) {
            moduleTeachingFormRepository.replaceForModule(
                    id,
                    mergeTeachingFormIds(teachingFormIds, workload)
            );
        }

        return getById(id);
    }

    private void normalizeAndValidateUniqueModuleNumberForCreate(Module module) {
        module.setModuleNumber(normalizeModuleNumber(module.getModuleNumber()));
        findExistingModuleNumber(module.getStudyProgramId(), module.getModuleNumber())
                .ifPresent(existing -> {
                    throw duplicateModuleNumber(module.getModuleNumber());
                });
    }

    private void normalizeAndValidateUniqueModuleNumberForUpdate(Long moduleId, Module module, Module oldModule) {
        normalizeModulebookReference(module, oldModule);
        String normalizedModuleNumber = normalizeModuleNumber(module.getModuleNumber());
        boolean sameStudyProgram = Objects.equals(module.getStudyProgramId(), oldModule.getStudyProgramId());
        boolean sameModuleNumber = Objects.equals(
                moduleNumberKey(normalizedModuleNumber),
                moduleNumberKey(oldModule.getModuleNumber())
        );

        if (sameStudyProgram && sameModuleNumber) {
            module.setModuleNumber(oldModule.getModuleNumber());
            return;
        }

        module.setModuleNumber(normalizedModuleNumber);
        findExistingModuleNumber(module.getStudyProgramId(), module.getModuleNumber())
                .filter(existing -> !Objects.equals(existing.getId(), moduleId))
                .ifPresent(existing -> {
                    throw duplicateModuleNumber(module.getModuleNumber());
                });
    }

    private Optional<Module> findExistingModuleNumber(Long studyProgramId, String moduleNumber) {
        if (studyProgramId == null || moduleNumber == null || moduleNumber.isBlank()) {
            return Optional.empty();
        }

        Optional<Module> existing = moduleRepository.findByStudyProgramIdAndModuleNumber(studyProgramId, moduleNumber);
        return existing == null ? Optional.empty() : existing;
    }

    private String normalizeModuleNumber(String moduleNumber) {
        if (moduleNumber == null) {
            return null;
        }

        return moduleNumber.trim();
    }

    private String moduleNumberKey(String moduleNumber) {
        String normalized = normalizeModuleNumber(moduleNumber);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException duplicateModuleNumber(String moduleNumber) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Module number " + moduleNumber + " already exists in this study program"
        );
    }


    @Transactional
    public void delete(Long id, String confirmation) {
        if (!"DELETE".equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type DELETE to delete this module");
        }

        Module module = getById(id);
        changeSetService.create(
                module.getStudyProgramId(),
                StudyProgramChangeSetService.SOURCE_DELETE,
                deleteReason(module)
        );
        boolean deleted = moduleRepository.delete(id);

        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found");
        }
    }

    private String deleteReason(Module module) {
        String label = moduleLabel(module);
        if (label.isBlank()) {
            return "deleted module " + module.getId();
        }
        return "deleted module " + label;
    }

    private String moduleLabel(Module module) {
        String moduleNumber = module.getModuleNumber() == null ? "" : module.getModuleNumber().trim();
        String name = module.getName() == null ? "" : module.getName().trim();
        return (moduleNumber + " " + name).trim();
    }

    private Module copyModule(Module source) {
        return new Module(
                source.getId(),
                source.getStudyProgramId(),
                source.getModuleNumber(),
                source.getName(),
                source.getCredits(),
                source.getDescription(),
                source.getModulebookId(),
                source.getModuleType(),
                source.getTitleDe(),
                source.getTitleEn(),
                source.getCoordinator(),
                source.getApplicability(),
                source.getLearningArea(),
                source.getLearningObjectives(),
                source.getPrerequisites(),
                source.getLevel(),
                source.getModuleStatus(),
                source.getModuleComments(),
                source.getFrequency(),
                source.getExaminationForm(),
                source.getContents(),
                source.getLiterature(),
                source.getAdditionalNotes(),
                source.getModuleWeight(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }

    public Optional<ModuleWorkload> getWorkloadByModuleId(Long moduleId) {
        return workloadRepository.findByModuleId(moduleId);
    }

    public List<TeachingForm> getTeachingFormsByModuleId(Long moduleId) {
        return moduleTeachingFormRepository.findByModuleId(moduleId);
    }

    private List<Long> teachingFormIdsByModuleId(Long moduleId) {
        return moduleTeachingFormRepository.findByModuleId(moduleId)
                .stream()
                .map(TeachingForm::getId)
                .toList();
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

    private String normalizeActiveStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Module status is required");
        }

        String normalized = status.trim().toUpperCase();
        if (!ACTIVE_STATUS.equals(normalized) && !DEACTIVE_STATUS.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Module status must be ACTIVE or DEACTIVE");
        }
        return normalized;
    }

    private Long createChangeSet(Long studyProgramId, String reason, String sourceType) {
        return changeSetService.create(studyProgramId, sourceType, reason).getId();
    }

    private void ensureModulebookId(Module module) {
        ensureModulebookId(module, null);
    }

    private void ensureModulebookId(Module module, Module oldModule) {
        if (module.getModulebookId() != null) {
            return;
        }
        if (oldModule != null && oldModule.getModulebookId() != null) {
            module.setModulebookId(oldModule.getModulebookId());
            return;
        }
        if (module.getStudyProgramId() != null) {
            module.setModulebookId(modulebookService.getOrCreateForStudyProgramId(module.getStudyProgramId()).getId());
        }
    }

    private void normalizeModulebookReference(Module module) {
        normalizeModulebookReference(module, null);
    }

    private void normalizeModulebookReference(Module module, Module oldModule) {
        if (module.getStudyProgramId() == null && module.getModulebookId() != null) {
            module.setStudyProgramId(module.getModulebookId());
        }
        if (module.getModulebookId() == null && module.getStudyProgramId() != null) {
            module.setModulebookId(module.getStudyProgramId());
        }
        if (module.getStudyProgramId() == null && oldModule != null) {
            module.setStudyProgramId(oldModule.getStudyProgramId());
        }
        if (module.getModulebookId() == null && oldModule != null) {
            module.setModulebookId(oldModule.getModulebookId());
        }
    }
}
