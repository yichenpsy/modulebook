package de.bht.modulebook.pdfimport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.modulebook.Modulebook;
import de.bht.modulebook.modulebook.ModulebookService;
import de.bht.modulebook.modulebook.ModulebookVersionService;
import de.bht.modulebook.pdfimport.dto.SaveImportedModuleError;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesRequest;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesResponse;
import de.bht.modulebook.modulebook.changeset.ModulebookChangeSet;
import de.bht.modulebook.modulebook.changeset.ModulebookChangeSetService;
import de.bht.modulebook.teachingform.TeachingForm;
import de.bht.modulebook.teachingform.TeachingFormRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PdfImportService {
    private static final String PDF_REIMPORT_VERSION_REASON = "before PDF re-import";
    private static final String MISSING_FROM_IMPORT_NOTE = "MISSING_FROM_IMPORT";
    private static final String MISSING_FROM_IMPORT_VERSION_REASON = "before marking missing from PDF re-import";

    private final PythonPdfImportRunner runner;
    private final PdfImportProperties properties;
    private final ObjectMapper objectMapper;
    private final ModuleService moduleService;
    private final PdfImportedModuleMapper importedModuleMapper;
    private final ModulebookService modulebookService;
    private final ModulebookChangeSetService changeSetService;
    private final TeachingFormRepository teachingFormRepository;
    private final ModulebookVersionService modulebookVersionService;

    public PdfImportService(
        PythonPdfImportRunner runner,
        PdfImportProperties properties,
        ObjectMapper objectMapper,
        ModuleService moduleService,
        PdfImportedModuleMapper importedModuleMapper,
        ModulebookService modulebookService,
        ModulebookChangeSetService changeSetService,
        TeachingFormRepository teachingFormRepository,
        ModulebookVersionService modulebookVersionService
    ){
        this.runner = runner;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.moduleService = moduleService;
        this.importedModuleMapper = importedModuleMapper;
        this.modulebookService = modulebookService;
        this.changeSetService = changeSetService;
        this.teachingFormRepository = teachingFormRepository;
        this.modulebookVersionService = modulebookVersionService;
    }

    public Map<String, JsonNode> importPdf(
            Long modulebookId,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate,
            MultipartFile file
    ) throws Exception {
        Modulebook modulebook = modulebookService.getById(modulebookId);

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

        Map<String, JsonNode> response = new HashMap<>();
        response.put("modulebookId", objectMapper.valueToTree(modulebook.getId()));
        response.put("studyProgram", objectMapper.valueToTree(modulebook.getStudyProgramName()));
        response.put("degreeType", objectMapper.valueToTree(modulebook.getDegreeType()));
        response.put("modulebookReleaseDate", objectMapper.valueToTree(modulebookReleaseDate));
        response.put("studyExaminationRegulationsDate", objectMapper.valueToTree(studyExaminationRegulationsDate));
        response.put("report", report);
        response.put("normalizedModules", normalizedModules);
        return response;
    }

    public SaveImportedModulesResponse saveImportedModules(SaveImportedModulesRequest request) {
        if (request == null || request.modules() == null || request.modules().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No modules provided");
        }

        List<ModuleResponse> createdModules = new ArrayList<>();
        List<SaveImportedModuleError> errors = new ArrayList<>();
        Modulebook modulebook = modulebookService.getById(request.modulebookId());
        String changeSetReason = "Import " + modulebook.getStudyProgramName() + " " + modulebook.getDegreeType() + " PDF";
        ModulebookChangeSet changeSet = changeSetService.create(
                modulebook.getId(),
                ModulebookChangeSetService.SOURCE_PDF_IMPORT,
                changeSetReason
        );
        List<Module> existingModules = moduleService.getByModulebookId(modulebook.getId());
        Set<Long> importedModuleIds = new HashSet<>();
        Set<String> importedModuleNumbers = new HashSet<>();
        Map<String, Long> teachingFormIdsByCode = teachingFormRepository.findAll()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        TeachingForm::getCode,
                        TeachingForm::getId
                ));

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
                Module module = importedModuleMapper.toModule(imported, modulebook.getStudyProgramName(), modulebook.getDegreeType());
                ModuleWorkload workload = importedModuleMapper.toWorkload(imported, teachingFormIdsByCode);
                module.setModulebookId(modulebook.getId());
                module.setModulebookId(modulebook.getId());
                module.setModuleStatus(ModuleService.ACTIVE_STATUS);
                module.setModuleComments(null);
                Module saved;

                if (moduleId != null) {
                    Module existing = moduleService.getById(moduleId);
                    module.setModuleStatus(existing.getModuleStatus());
                    saved = moduleService.update(moduleId, module, workload, List.of(), PDF_REIMPORT_VERSION_REASON, changeSet.getId());
                } else if (module.getModuleNumber() != null && !module.getModuleNumber().isBlank()) {
                    Module existing = moduleService
                        .findByModulebookIdAndModuleNumber(modulebook.getId(), module.getModuleNumber())
                        .orElse(null);

                    if (existing == null) {
                        saved = moduleService.create(module, workload, List.of(), ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON, changeSet.getId());
                    } else {
                        module.setModuleStatus(existing.getModuleStatus());
                        saved = moduleService.update(existing.getId(), module, workload, List.of(), PDF_REIMPORT_VERSION_REASON, changeSet.getId());
                    }
                } else {
                    saved = moduleService.create(module, workload, List.of(), ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON, changeSet.getId());
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
        createInitialImportVersionIfNeeded(
                modulebook.getId(),
                changeSet.getId(),
                request.modulebookReleaseDate(),
                request.studyExaminationRegulationsDate()
        );

        return new SaveImportedModulesResponse(
            request.modules().size(),
            createdModules.size(),
            errors.size(),
            changeSet.getId(),
            createdModules,
            errors
        );
    }

    private void createInitialImportVersionIfNeeded(
            Long modulebookId,
            Long changeSetId,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate
    ) {
        if (changeSetService.isFirstSourceChangeSet(
                modulebookId,
                ModulebookChangeSetService.SOURCE_PDF_IMPORT,
                changeSetId
        )) {
            modulebookVersionService.createInitialImportVersion(
                    modulebookId,
                    changeSetId,
                    modulebookReleaseDate,
                    studyExaminationRegulationsDate
            );
        }
    }

    private void markModulesMissingFromImport(
            List<Module> existingModules,
            Set<Long> importedModuleIds,
            Set<String> importedModuleNumbers,
            Long changeSetId
    ) {
        for (Module existing : existingModules) {
            if (wasSeenInImport(existing, importedModuleIds, importedModuleNumbers)
                    || MISSING_FROM_IMPORT_NOTE.equals(existing.getModuleComments())) {
                continue;
            }

            Module missing = copyModule(existing);
            missing.setModuleComments(MISSING_FROM_IMPORT_NOTE);
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
                source.getModulebookId(),
                source.getModuleNumber(),
                source.getName(),
                source.getCredits(),
                source.getDescription(),
                source.getModuleType(),
                source.getTitleDe(),
                source.getTitleEn(),
                source.getCoordinator(),
                source.getApplicability(),
                source.getLearningArea(),
                source.getLearningObjectives(),
                source.getPrerequisites(),
                source.getLevel(),
                source.getModuleStatus(),
                source.getModuleComments(),
                source.getFrequency(),
                source.getExaminationForm(),
                source.getContents(),
                source.getLiterature(),
                source.getAdditionalNotes(),
                source.getModuleWeight(),
                source.getCreatedAt(),
                source.getUpdatedAt(),
                source.getCreatedChangeSetId()
        );
    }

    private String errorMessage(Exception exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}
