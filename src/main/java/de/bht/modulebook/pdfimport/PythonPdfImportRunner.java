package de.bht.modulebook.pdfimport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.bht.modulebook.config.PdfImportProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// builds and runs this command:
// python -m tools.pdf_import.module_import_v2.pipeline_single ...
@Service
public class PythonPdfImportRunner {

    private final PdfImportProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public PythonPdfImportRunner(PdfImportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public ImportRunResult run(
            Path inputPdf,
            Path workDir,
            Path outputJson,
            Path reportJson,
            String sourceFile
    ) throws IOException, InterruptedException {

        Files.createDirectories(workDir);

        if (hasPdfImportServiceUrl()) {
            return runViaHttpService(inputPdf, outputJson, reportJson, sourceFile);
        }

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

        // start pipeline_single.py 
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

    private boolean hasPdfImportServiceUrl() {
        return properties.getServiceUrl() != null && !properties.getServiceUrl().isBlank();
    }

    private ImportRunResult runViaHttpService(
            Path inputPdf,
            Path outputJson,
            Path reportJson,
            String sourceFile
    ) throws IOException {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new FileSystemResource(inputPdf));
        if (sourceFile != null && !sourceFile.isBlank()) {
            bodyBuilder.part("sourceFile", sourceFile);
        }
        MultiValueMap<String, org.springframework.http.HttpEntity<?>> body = bodyBuilder.build();

        try {
            JsonNode response = restClient
                    .post()
                    .uri(properties.getServiceUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new RuntimeException("PDF import service returned an empty response");
            }

            JsonNode normalizedModules = response.get("normalizedModules");
            JsonNode report = response.get("report");

            if (normalizedModules == null || report == null) {
                throw new RuntimeException("PDF import service response must contain normalizedModules and report");
            }

            objectMapper.writeValue(outputJson.toFile(), normalizedModules);
            objectMapper.writeValue(reportJson.toFile(), report);

            return new ImportRunResult(
                    0,
                    outputJson,
                    reportJson,
                    response.toString(),
                    ""
            );
        } catch (RestClientException exception) {
            throw new RuntimeException("PDF import service request failed: " + exception.getMessage(), exception);
        }
    }
}
