package de.bht.modulebook.module;

import java.util.ArrayList;
import java.util.HashSet;
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
import de.bht.modulebook.module.version.ModuleChangeLogService;
// import de.bht.modulebook.module.version.ModuleVersionService;
import de.bht.modulebook.teachingform.TeachingForm;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;

// logic layer, checkes things like "module not found", calls repository
@Service
public class ModuleService {
    public static final String ACTIVE_STATUS = "ACTIVE";
    public static final String DELETED_STATUS = "DELETED";
    public static final String MANUAL_INITIAL_VERSION_REASON = "created manually";
    public static final String PDF_IMPORT_INITIAL_VERSION_REASON = "created from PDF import";

    private final ModuleRepository moduleRepository;
    private final ModuleWorkloadRepository workloadRepository;
    private final ModuleTeachingFormRepository moduleTeachingFormRepository;
    //private final ModuleVersionService versionService;
    private final StudyProgramChangeSetService changeSetService;
    private final ModuleChangeLogService changeLogService;
    

    public ModuleService(
        ModuleRepository moduleRepository,
        ModuleWorkloadRepository workloadRepository,
        ModuleTeachingFormRepository moduleTeachingFormRepository,
        // ModuleVersionService versionService,
        StudyProgramChangeSetService changeSetService,
        ModuleChangeLogService changeLogService
    ) {
        // The service needs the repository to access the database.
        this.moduleRepository = moduleRepository;
        this.workloadRepository = workloadRepository;
        this.moduleTeachingFormRepository = moduleTeachingFormRepository;
        //this.versionService = versionService;
        this.changeSetService = changeSetService;
        this.changeLogService = changeLogService;
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
            workloadRepository.upsert(created.getId(), workload);
        }
        moduleTeachingFormRepository.replaceForModule(
                created.getId(),
                mergeTeachingFormIds(teachingFormIds, workload)
        );
        //versionService.saveSnapshot(created.getId(), normalizeInitialVersionReason(initialVersionReason), changeSetId);
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
        Module oldModule = getById(id);
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(id);
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
        ModuleWorkload oldWorkload = workloadRepository.findByModuleId(id).orElse(null);
        List<Long> oldTeachingFormIds = teachingFormIdsByModuleId(id);
        var changeSet = changeSetService.create(
            oldModule.getStudyProgramId(),
            StudyProgramChangeSetService.SOURCE_MANUAL_EDIT,
            normalizeVersionReason(versionReason)
        );
        preserveUnchangedModuleFields(module, oldModule, changedFieldPaths);
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


    @Transactional
    public void delete(Long id) {
        Module module = getById(id);
        changeSetService.create(
                module.getStudyProgramId(),
                StudyProgramChangeSetService.SOURCE_DELETE,
                deleteReason(module)
        );
        boolean deleted = moduleRepository.delete(id);

        // The repository returns false when no row with this id exists.
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

    private Long createChangeSet(Long studyProgramId, String reason, String sourceType) {
        return changeSetService.create(studyProgramId, sourceType, reason).getId();
    }
}
