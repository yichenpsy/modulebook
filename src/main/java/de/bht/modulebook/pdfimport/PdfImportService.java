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
import de.bht.modulebook.modulebook.ModulebookTagService;
import de.bht.modulebook.pdfimport.dto.SaveImportedModuleError;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesRequest;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesResponse;
import de.bht.modulebook.studyprogram.StudyProgram;
import de.bht.modulebook.studyprogram.StudyProgramService;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSet;
import de.bht.modulebook.studyprogram.changeset.StudyProgramChangeSetService;
import de.bht.modulebook.teachingform.TeachingForm;
import de.bht.modulebook.teachingform.TeachingFormRepository;
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
    private final TeachingFormRepository teachingFormRepository;
    private final ModulebookTagService modulebookTagService;

    public PdfImportService(
        PythonPdfImportRunner runner,
        PdfImportProperties properties,
        ObjectMapper objectMapper,
        ModuleService moduleService,
        PdfImportedModuleMapper importedModuleMapper,
        StudyProgramService studyProgramService,
        StudyProgramChangeSetService changeSetService,
        TeachingFormRepository teachingFormRepository,
        ModulebookTagService modulebookTagService
    ){
        this.runner = runner;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.moduleService = moduleService;
        this.importedModuleMapper = importedModuleMapper;
        this.studyProgramService = studyProgramService;
        this.changeSetService = changeSetService;
        this.teachingFormRepository = teachingFormRepository;
        this.modulebookTagService = modulebookTagService;
    }

    public Map<String, JsonNode> importPdf(
            Long studyProgramId,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate,
            MultipartFile file
    ) throws Exception {
        StudyProgram studyProgram = studyProgramService.getById(studyProgramId);

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
        response.put("studyProgramId", objectMapper.valueToTree(studyProgram.getId()));
        response.put("studyProgram", objectMapper.valueToTree(studyProgram.getName()));
        response.put("degreeType", objectMapper.valueToTree(studyProgram.getDegreeType()));
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
        StudyProgram studyProgram = studyProgramService.getById(request.studyProgramId());
        String changeSetReason = "Re-import " + studyProgram.getName() + " " + studyProgram.getDegreeType() + " PDF";
        StudyProgramChangeSet changeSet = changeSetService.create(
                studyProgram.getId(),
                StudyProgramChangeSetService.SOURCE_PDF_IMPORT,
                changeSetReason
        );
        List<Module> existingModules = moduleService.getByStudyProgramId(studyProgram.getId());
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
                Module module = importedModuleMapper.toModule(imported, studyProgram.getName(), studyProgram.getDegreeType());
                ModuleWorkload workload = importedModuleMapper.toWorkload(imported, teachingFormIdsByCode);
                module.setStudyProgramId(studyProgram.getId());
                module.setModuleStatus(ModuleService.ACTIVE_STATUS);
                Module saved;

                if (moduleId != null) {
                    saved = moduleService.update(moduleId, module, workload, List.of(), PDF_REIMPORT_VERSION_REASON, changeSet.getId());
                } else if (module.getModuleNumber() != null && !module.getModuleNumber().isBlank()) {
                    Module existing = moduleService
                        .findByStudyProgramIdAndModuleNumber(studyProgram.getId(), module.getModuleNumber())
                        .orElse(null);

                    if (existing == null) {
                        saved = moduleService.create(module, workload, List.of(), ModuleService.PDF_IMPORT_INITIAL_VERSION_REASON, changeSet.getId());
                    } else {
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
        createInitialImportTagIfNeeded(
                studyProgram.getId(),
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

    private void createInitialImportTagIfNeeded(
            Long studyProgramId,
            Long changeSetId,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate
    ) {
        if (changeSetService.isFirstSourceChangeSet(
                studyProgramId,
                StudyProgramChangeSetService.SOURCE_PDF_IMPORT,
                changeSetId
        )) {
            modulebookTagService.createInitialImportTag(
                    studyProgramId,
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
                    || MISSING_FROM_IMPORT_STATUS.equals(existing.getModuleStatus())
                    || ModuleService.DELETED_STATUS.equals(existing.getModuleStatus())) {
                continue;
            }

            Module missing = copyModule(existing);
            missing.setModuleStatus(MISSING_FROM_IMPORT_STATUS);
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
                source.getModuleStatus(),
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
