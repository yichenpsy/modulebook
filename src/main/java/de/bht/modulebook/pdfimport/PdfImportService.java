package de.bht.modulebook.pdfimport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import de.bht.modulebook.config.PdfImportProperties;
import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.ModuleMapper;
import de.bht.modulebook.module.ModuleService;
import de.bht.modulebook.module.dto.ModuleResponse;
import de.bht.modulebook.pdfimport.dto.SaveImportedModuleError;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesRequest;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PdfImportService {
    private final PythonPdfImportRunner runner;
    private final PdfImportProperties properties;
    private final ObjectMapper objectMapper;
    private final ModuleService moduleService;
    private final PdfImportedModuleMapper importedModuleMapper;

    public PdfImportService(
        PythonPdfImportRunner runner,
        PdfImportProperties properties,
        ObjectMapper objectMapper,
        ModuleService moduleService,
        PdfImportedModuleMapper importedModuleMapper
    ){
        this.runner = runner;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.moduleService = moduleService;
        this.importedModuleMapper = importedModuleMapper;
    }

    public Map<String, JsonNode> importPdf(String studyProgram, Integer year, MultipartFile file) throws Exception {
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

        JsonNode normalizedModules = objectMapper.readTree(outputJson.toFile());
        JsonNode report = objectMapper.readTree(reportJson.toFile());

        return Map.of(
            "studyProgram", objectMapper.valueToTree(studyProgram),
            "year", objectMapper.valueToTree(year),
            "report", report,
            "normalizedModules", normalizedModules
        );
    }

    public SaveImportedModulesResponse saveImportedModules(SaveImportedModulesRequest request) {
        if (request == null || request.modules() == null || request.modules().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No modules provided");
        }

        List<ModuleResponse> createdModules = new ArrayList<>();
        List<SaveImportedModuleError> errors = new ArrayList<>();

        for (int i = 0; i < request.modules().size(); i++) {
            JsonNode imported = request.modules().get(i);
            String moduleNumber = importedModuleMapper.moduleNumber(imported);

            try {
                Module module = importedModuleMapper.toModule(imported, request.studyProgram(), request.year());
                Module created = moduleService.create(module);
                createdModules.add(ModuleMapper.toResponse(created));
            } catch (Exception exception) {
                errors.add(new SaveImportedModuleError(
                    i,
                    moduleNumber,
                    errorMessage(exception)
                ));
            }
        }

        return new SaveImportedModulesResponse(
            request.modules().size(),
            createdModules.size(),
            errors.size(),
            createdModules,
            errors
        );
    }

    private String errorMessage(Exception exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}
