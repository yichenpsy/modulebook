package de.bht.modulebook.module.version;

import java.util.List;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.workload.ModuleWorkload;

public class ModuleSnapshot {
    private Module module;
    private ModuleWorkload workload;
    private List<Long> teachingFormIds = List.of();

    public ModuleSnapshot() {
    }

    public ModuleSnapshot(Module module, ModuleWorkload workload, List<Long> teachingFormIds) {
        this.module = module;
        this.workload = workload;
        this.teachingFormIds = teachingFormIds == null ? List.of() : teachingFormIds;
    }

    public Module getModule() {
        return module;
    }

    public void setModule(Module module) {
        this.module = module;
    }

    public ModuleWorkload getWorkload() {
        return workload;
    }

    public void setWorkload(ModuleWorkload workload) {
        this.workload = workload;
    }

    public List<Long> getTeachingFormIds() {
        return teachingFormIds;
    }

    public void setTeachingFormIds(List<Long> teachingFormIds) {
        this.teachingFormIds = teachingFormIds == null ? List.of() : teachingFormIds;
    }
}
