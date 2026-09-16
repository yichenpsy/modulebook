package de.bht.modulebook.pdfimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import de.bht.modulebook.config.PdfImportProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// builds and runs this command:
// python -m tools.pdf_import.module_import_v2.pipeline_single ...
@Service
public class PythonPdfImportRunner {

    private final PdfImportProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PythonPdfImportRunner(PdfImportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
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
    ) throws IOException, InterruptedException {
        String boundary = "modulebook-" + UUID.randomUUID();
        byte[] body = multipartBody(inputPdf, sourceFile, boundary);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getServiceUrl()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            throw new RuntimeException(
                    "PDF import service request failed: HTTP "
                            + httpResponse.statusCode()
                            + ": "
                            + httpResponse.body()
            );
        }

        JsonNode response = objectMapper.readTree(httpResponse.body());
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
    }

    private byte[] multipartBody(Path inputPdf, String sourceFile, String boundary) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String filename = sourceFile;
        if (filename == null || filename.isBlank()) {
            filename = inputPdf.getFileName().toString();
        }

        writeText(body, "--" + boundary + "\r\n");
        writeText(body, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        writeText(body, "Content-Type: application/pdf\r\n\r\n");
        body.write(Files.readAllBytes(inputPdf));
        writeText(body, "\r\n");

        writeText(body, "--" + boundary + "\r\n");
        writeText(body, "Content-Disposition: form-data; name=\"sourceFile\"\r\n\r\n");
        writeText(body, filename);
        writeText(body, "\r\n");

        writeText(body, "--" + boundary + "--\r\n");
        return body.toByteArray();
    }

    private void writeText(ByteArrayOutputStream outputStream, String value) throws IOException {
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
    }
}
