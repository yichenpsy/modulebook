package de.bht.modulebook.module.version;

import java.util.List;

public class ModuleDiff {
    private Integer fromVersionNumber;
    private Integer toVersionNumber;
    private List<FieldChange> changes;

    public ModuleDiff(Integer fromVersionNumber, Integer toVersionNumber, List<FieldChange> changes) {
        this.fromVersionNumber = fromVersionNumber;
        this.toVersionNumber = toVersionNumber;
        this.changes = changes;
    }

    public Integer getFromVersionNumber() {
        return fromVersionNumber;
    }

    public Integer getToVersionNumber() {
        return toVersionNumber;
    }

    public List<FieldChange> getChanges() {
        return changes;
    }

    public static class FieldChange {
        private String path;
        private String oldValue;
        private String newValue;

        public FieldChange(String path, String oldValue, String newValue) {
            this.path = path;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getPath() {
            return path;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }
    }
}
