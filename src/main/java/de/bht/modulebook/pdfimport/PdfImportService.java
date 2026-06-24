package de.bht.modulebook.pdfimport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.bht.modulebook.config.PdfImportProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PdfImportService {
    private final PythonPdfImportRunner runner;
    private final PdfImportProperties properties;
    private final ObjectMapper objectMapper;

    public PdfImportService(
        PythonPdfImportRunner runner,
        PdfImportProperties properties,
        ObjectMapper objectMapper
    ){
        this.runner = runner;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public JsonNode importPdf(MultipartFile file) throws Exception {
        // check empty file
        if (file.isEmpty()){
            throw new IllegalArgumentException("PDF file is empty");
        }

        // check File name
        String originalFilename = file.getOriginalFilename();
        if(originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }

        // create job folder
        Path jobRoot = Path.of(properties.getJobRoot());
        // each upload has a unique path
        Path workDir = jobRoot.resolve(UUID.randomUUID().toString());
        Files.createDirectories(workDir);

        // save uploaded PDF
        Path inputPdf = workDir.resolve("input.pdf");
        file.transferTo(inputPdf);

        // define output path
        Path outputJson = workDir.resolve("normalized_modules.json");
        Path reportJson = workDir.resolve("report.json");

        ImportRunResult result = runner.run(inputPdf, workDir, outputJson, reportJson, originalFilename);

        if (!result.succeeded()) {
            throw new RuntimeException("PDF import failed: " + result.stderr());
        }

        return objectMapper.readTree(outputJson.toFile());
    }

    
}
