package de.bht.modulebook.pdfimport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import de.bht.modulebook.module.Module;
import de.bht.modulebook.module.workload.ModuleWorkload;
import de.bht.modulebook.module.workload.PresenceHourComponent;
import tools.jackson.databind.JsonNode;

@Component
public class PdfImportedModuleMapper {
    private static final Pattern SWS_COMPONENT_PATTERN = Pattern.compile(
            "(\\d+(?:[,.]\\d+)?)\\s*SWS\\s*([\\p{L}]+)",
            Pattern.CASE_INSENSITIVE
    );

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
        module.setModuleNumber(moduleNumber);
        module.setName(title);
        module.setCredits(credits);
        module.setDescription(buildDescription(imported, studyProgram, year));
        module.setTitleDe(text(imported, "title_de"));
        module.setTitleEn(text(imported, "title_en"));
        module.setApplicability(text(imported, "applicability"));
        module.setLearningArea(text(imported, "learning_area"));
        module.setLearningObjectives(text(imported, "learning_objectives"));
        module.setPrerequisites(text(imported, "prerequisites"));
        module.setLevel(text(imported, "level"));
        module.setModuleType(text(imported, "status"));
        module.setFrequency(text(imported, "frequency"));
        module.setExaminationForm(text(imported, "exam_form"));
        module.setContents(text(imported, "contents"));
        module.setLiterature(text(imported, "literature"));
        module.setAdditionalNotes(text(imported, "additional_notes"));

        return module;
    }

    public ModuleWorkload toWorkload(JsonNode imported, Map<String, Long> teachingFormIdsByCode) {
        String rawText = text(imported, "workload");
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        ModuleWorkload workload = new ModuleWorkload();
        workload.setRawText(rawText);

        List<PresenceHourComponent> components = new ArrayList<>();
        Matcher matcher = SWS_COMPONENT_PATTERN.matcher(rawText);
        while (matcher.find()) {
            String normalizedCode = normalizeTeachingFormCode(matcher.group(2));
            Long teachingFormId = teachingFormIdsByCode.get(normalizedCode);
            if (teachingFormId == null) {
                continue;
            }

            PresenceHourComponent component = new PresenceHourComponent();
            component.setTeachingFormId(teachingFormId);
            component.setSwsAmount(new BigDecimal(matcher.group(1).replace(",", ".")));
            components.add(component);
        }

        workload.setComponents(components);
        return workload;
    }

    public String moduleNumber(JsonNode imported) {
        return text(imported, "module_number");
    }

    public Long moduleId(JsonNode imported) {
        if (imported == null || imported.isNull()) {
            return null;
        }

        JsonNode value = imported.get("id");
        if (value == null || value.isNull() || value.asString().isBlank()) {
            return null;
        }

        return value.asLong();
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

    private String normalizeTeachingFormCode(String code) {
        if (code == null) {
            return "";
        }

        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "Ü", "ÜBUNG", "UEBUNG" -> "UE";
            case "PRAKTIKUM" -> "P";
            case "VORLESUNG" -> "V";
            case "SEMINAR" -> "S";
            default -> normalized;
        };
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
