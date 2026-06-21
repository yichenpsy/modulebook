package de.bht.modulebook.controller;

import de.bht.modulebook.pdfimport.ImportRunResult;
import de.bht.modulebook.pdfimport.PythonPdfImportRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
public class PdfImportTestController {

    private final PythonPdfImportRunner runner;

    public PdfImportTestController(PythonPdfImportRunner runner) {
        this.runner = runner;
    }

    @GetMapping("/api/pdf-import/test-architektur")
    public ImportRunResult testArchitektur() throws Exception {
        Path inputPdf = Path.of("tools/pdf_import/data/input/architektur_2021.pdf");
        Path workDir = Path.of("tools/pdf_import/data/import_jobs/test-architektur");
        Path outputJson = workDir.resolve("normalized_modules.json");
        Path reportJson = workDir.resolve("report.json");

        return runner.run(
            inputPdf,
            workDir,
            outputJson,
            reportJson,
            "tools/pdf_import/data/input/architektur_2021.pdf"
        );
    }
}