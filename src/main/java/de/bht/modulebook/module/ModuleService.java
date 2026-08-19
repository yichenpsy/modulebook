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
import de.bht.modulebook.teachingform.TeachingForm;

// logic layer, checkes things like "module not found", calls repository
@Service
public class ModuleService {
    private static final BigDecimal WEEKS_PER_SEMESTER = BigDecimal.valueOf(17);
    private static final BigDecimal HOURS_PER_CREDIT = BigDecimal.valueOf(30);

    private final ModuleRepository moduleRepository;
    private final ModuleWorkloadRepository workloadRepository;
    private final ModuleTeachingFormRepository moduleTeachingFormRepository;

    public ModuleService(
        ModuleRepository moduleRepository,
        ModuleWorkloadRepository workloadRepository,
        ModuleTeachingFormRepository moduleTeachingFormRepository
    ) {
        // The service needs the repository to access the database.
        this.moduleRepository = moduleRepository;
        this.workloadRepository = workloadRepository;
        this.moduleTeachingFormRepository = moduleTeachingFormRepository;
    }

    public Module create(Module module) {
        return create(module, null);
    }

    public Module create(Module module, ModuleWorkload workload) {
        return create(module, workload, List.of());
    }

    @Transactional
    public Module create(Module module, ModuleWorkload workload, List<Long> teachingFormIds) {
        Module created = moduleRepository.insert(module);
        if (workload != null) {
            calculateWorkload(created, workload);
            workloadRepository.upsert(created.getId(), workload);
        }
        moduleTeachingFormRepository.replaceForModule(
                created.getId(),
                mergeTeachingFormIds(teachingFormIds, workload)
        );
        return getById(created.getId());
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


    public void delete(Long id) {
        boolean deleted = moduleRepository.delete(id);

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
}
