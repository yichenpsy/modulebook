package de.bht.modulebook.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.bht.modulebook.pdfimport.PdfImportService;
import tools.jackson.databind.JsonNode;

@RestController
public class PdfImportController {
    private final PdfImportService pdfImportService;

    public PdfImportController(PdfImportService pdfImportService){
        this.pdfImportService = pdfImportService;
    }

    @PostMapping("/api/pdf-import/normalize")
    public Map<String, JsonNode> normalize(@RequestParam("file") MultipartFile file) throws Exception {
        return pdfImportService.importPdf(file);
    }

    

}
