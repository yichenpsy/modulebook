package de.bht.modulebook.pdfimport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import de.bht.modulebook.config.PdfImportProperties;

@Service
public class PythonPdfImportRunner {

    private final PdfImportProperties properties;

    public PythonPdfImportRunner(PdfImportProperties properties) {
        this.properties = properties;
    }

    public ImportRunResult run(
            Path inputPdf,
            Path workDir,
            Path outputJson,
            Path reportJson,
            String sourceFile
    ) throws IOException, InterruptedException {

        Files.createDirectories(workDir);

        List<String> command = new ArrayList<>();
        command.add(properties.getPythonExecutable());
        command.add("-m");
        command.add("tools.pdf_import.module_import_v2.pipeline_single");
        command.add("--input");
        command.add(inputPdf.toAbsolutePath().toString());
        command.add("--work-dir");
        command.add(workDir.toAbsolutePath().toString());
        command.add("--output");
        command.add(outputJson.toAbsolutePath().toString());
        command.add("--report");
        command.add(reportJson.toAbsolutePath().toString());
        command.add("--base-yaml");
        command.add(properties.getBaseYaml());
        command.add("--import-manifest");
        command.add(properties.getImportManifest());

        if (sourceFile != null && !sourceFile.isBlank()) {
            command.add("--source-file");
            command.add(sourceFile);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(Path.of(properties.getRepoRoot()).toFile());

        Process process = processBuilder.start();

        boolean finished = process.waitFor(
            properties.getTimeoutSeconds(),
            TimeUnit.SECONDS
        );

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("PDF import timed out");
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        return new ImportRunResult(
            process.exitValue(),
            outputJson,
            reportJson,
            stdout,
            stderr
        );
    }
}