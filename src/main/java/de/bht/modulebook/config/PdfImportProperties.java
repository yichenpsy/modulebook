package de.bht.modulebook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdf-import")
public class PdfImportProperties {
    private String pythonExecutable;
    private String repoRoot;
    private String jobRoot;
    private String baseYaml;
    private String importManifest;
    private long timeoutSeconds = 300;

    public String getPythonExecutable() {
        return pythonExecutable;
    }

    public void setPythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
    }

    public String getRepoRoot() {
        return repoRoot;
    }

    public void setRepoRoot(String repoRoot) {
        this.repoRoot = repoRoot;
    }

    public String getJobRoot() {
        return jobRoot;
    }

    public void setJobRoot(String jobRoot) {
        this.jobRoot = jobRoot;
    }

    public String getBaseYaml() {
        return baseYaml;
    }

    public void setBaseYaml(String baseYaml) {
        this.baseYaml = baseYaml;
    }

    public String getImportManifest() {
        return importManifest;
    }

    public void setImportManifest(String importManifest) {
        this.importManifest = importManifest;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}