package de.bht.modulebook.pdfimport;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.bht.modulebook.pdfimport.dto.SaveImportedModulesRequest;
import de.bht.modulebook.pdfimport.dto.SaveImportedModulesResponse;
import tools.jackson.databind.JsonNode;

@RestController
public class PdfImportController {
    private final PdfImportService pdfImportService;

    public PdfImportController(PdfImportService pdfImportService) {
        this.pdfImportService = pdfImportService;
    }

    @PostMapping("/api/pdf-import/normalize")
    public Map<String, JsonNode> normalize(
        @RequestParam("studyProgram") String studyProgram,
        @RequestParam("year") Integer year,
        @RequestParam("file") MultipartFile file
    ) throws Exception {
        return pdfImportService.importPdf(studyProgram, year, file);
    }

    @PostMapping("/api/pdf-import/modules")
    public SaveImportedModulesResponse saveImportedModules(@RequestBody SaveImportedModulesRequest request) {
        return pdfImportService.saveImportedModules(request);
    }
}
