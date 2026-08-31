package de.bht.modulebook.pdfimport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import de.bht.modulebook.studyprogram.StudyProgram;
import de.bht.modulebook.studyprogram.StudyProgramService;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSet;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PdfImportService {
    private static final String PDF_REIMPORT_VERSION_REASON = "before PDF re-import";
    private static final String MISSING_FROM_IMPORT_STATUS = "MISSING_FROM_IMPORT";
    private static final String MISSING_FROM_IMPORT_VERSION_REASON = "before marking missing from PDF re-import";

    private final PythonPdfImportRunner runner;
    private final PdfImportProperties properties;
    private final ObjectMapper objectMapper;
    private final ModuleService moduleService;
    private final PdfImportedModuleMapper importedModuleMapper;
    private final StudyProgramService studyProgramService;
    private final StudyProgramChangeSetService changeSetService;

    public PdfImportService(
        PythonPdfImportRunner runner,
        PdfImportProperties properties,
        ObjectMapper objectMapper,
        ModuleService moduleService,
        PdfImportedModuleMapper importedModuleMapper,
        StudyProgramService studyProgramService,
        StudyProgramChangeSetService changeSetService
    ){
        this.runner = runner;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.moduleService = moduleService;
        this.importedModuleMapper = importedModuleMapper;
        this.studyProgramService = studyProgramService;
        this.changeSetService = changeSetService;
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
        StudyProgram studyProgram = studyProgramService.findOrCreate(request.studyProgram(), request.year());
        String changeSetReason = "Re-import " + studyProgram.getName() + " " + studyProgram.getYear() + " PDF";
        StudyProgramChangeSet changeSet = changeSetService.create(
                studyProgram.getId(),
                StudyProgramChangeSetService.SOURCE_PDF_IMPORT,
                changeSetReason
        );
        List<Module> existingModules = moduleService.getByStudyProgramId(studyProgram.getId());
        Set<Long> importedModuleIds = new HashSet<>();
        Set<String> importedModuleNumbers = new HashSet<>();

        for (int i = 0; i < request.modules().size(); i++) {
            JsonNode imported = request.modules().get(i);
            String moduleNumber = importedModuleMapper.moduleNumber(imported);
            Long moduleId = importedModuleMapper.moduleId(imported);

            if (moduleId != null) {
                importedModuleIds.add(moduleId);
            }
            if (moduleNumber != null && !moduleNumber.isBlank()) {
                importedModuleNumbers.add(moduleNumber.trim());
            }

            try {
                Module module = importedModuleMapper.toModule(imported, request.studyProgram(), request.year());
                module.setStudyProgramId(studyProgram.getId());
                module.setStatus(ModuleService.ACTIVE_STATUS);
                Module saved;

                if (moduleId != null) {
                    saved = moduleService.update(moduleId, module, PDF_REIMPORT_VERSION_REASON, changeSet.getId());
                } else if (module.getModuleNumber() != null && !module.getModuleNumber().isBlank()) {
                    Module existing = moduleService
                        .findByStudyProgramIdAndModuleNumber(studyProgram.getId(), module.getModuleNumber())
                        .orElse(null);

                    if (existing == null) {
                        saved = moduleService.createFromPdfImport(module, changeSet.getId());
                    } else {
                        saved = moduleService.update(existing.getId(), module, PDF_REIMPORT_VERSION_REASON, changeSet.getId());
                    }
                } else {
                    saved = moduleService.createFromPdfImport(module, changeSet.getId());
                }

                createdModules.add(ModuleMapper.toResponse(saved));
            } catch (Exception exception) {
                errors.add(new SaveImportedModuleError(
                    i,
                    moduleNumber,
                    errorMessage(exception)
                ));
            }
        }
        markModulesMissingFromImport(existingModules, importedModuleIds, importedModuleNumbers, changeSet.getId());

        return new SaveImportedModulesResponse(
            request.modules().size(),
            createdModules.size(),
            errors.size(),
            changeSet.getId(),
            createdModules,
            errors
        );
    }

    private void markModulesMissingFromImport(
            List<Module> existingModules,
            Set<Long> importedModuleIds,
            Set<String> importedModuleNumbers,
            Long changeSetId
    ) {
        for (Module existing : existingModules) {
            if (wasSeenInImport(existing, importedModuleIds, importedModuleNumbers)
                    || MISSING_FROM_IMPORT_STATUS.equals(existing.getStatus())
                    || ModuleService.DELETED_STATUS.equals(existing.getStatus())) {
                continue;
            }

            Module missing = copyModule(existing);
            missing.setStatus(MISSING_FROM_IMPORT_STATUS);
            moduleService.update(existing.getId(), missing, MISSING_FROM_IMPORT_VERSION_REASON, changeSetId);
        }
    }

    private boolean wasSeenInImport(Module module, Set<Long> importedModuleIds, Set<String> importedModuleNumbers) {
        if (module.getId() != null && importedModuleIds.contains(module.getId())) {
            return true;
        }

        String moduleNumber = module.getModuleNumber();
        return moduleNumber != null && importedModuleNumbers.contains(moduleNumber.trim());
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
                source.getStatus(),
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

    private String errorMessage(Exception exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}
