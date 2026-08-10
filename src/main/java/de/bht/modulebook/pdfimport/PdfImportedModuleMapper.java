package de.bht.modulebook.pdfimport;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import de.bht.modulebook.module.Module;
import tools.jackson.databind.JsonNode;

@Component
public class PdfImportedModuleMapper {
    public Module toModule(JsonNode imported, String studyProgram, Integer year) {
        if (imported == null || imported.isNull()) {
            throw new IllegalArgumentException("Missing imported module");
        }

        String moduleNumber = text(imported, "module_number");
        String title = text(imported, "title");
        BigDecimal credits = decimal(imported, "credits");

        if (moduleNumber == null || moduleNumber.isBlank()) {
            throw new IllegalArgumentException("Missing module_number");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Missing title");
        }

        if (credits == null) {
            throw new IllegalArgumentException("Missing credits");
        }

        Module module = new Module();
        module.setCode(moduleNumber);
        module.setName(title);
        module.setCredits(credits);
        module.setDescription(buildDescription(imported, studyProgram, year));

        return module;
    }

    public String moduleNumber(JsonNode imported) {
        return text(imported, "module_number");
    }

    private String buildDescription(JsonNode imported, String studyProgram, Integer year) {
        return """
                Study program: %s
                Year: %s

                Contents:
                %s

                Learning objectives:
                %s

                Prerequisites:
                %s

                Exam form:
                %s

                Literature:
                %s

                Source file:
                %s

                Source pages:
                %s
                """.formatted(
                studyProgram,
                year,
                text(imported, "contents"),
                text(imported, "learning_objectives"),
                text(imported, "prerequisites"),
                text(imported, "exam_form"),
                text(imported, "literature"),
                text(imported, "source_file"),
                imported.get("source_pages")
        );
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asString();
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return new BigDecimal(value.asString());
    }
}
