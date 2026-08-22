package de.bht.modulebook.module;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

// database layer, use JdbcTemplate to run SQL
@Repository
public class ModuleRepository {

    private static final String SELECT_COLUMNS = """
            id, study_program_id, module_number, name, credits, description,
            modulebook_id, module_type, title_de, title_en, coordinator, applicability,
            learning_area, learning_objectives, prerequisites, level, status, frequency,
            examination_form, contents, literature, additional_notes, module_weight, created_at, updated_at
            """;

    // JdbcTemplate is Spring's helper class for running SQL.
    private final JdbcTemplate jdbcTemplate;

    // SimpleJdbcInsert is configured once and reused for inserting modules.
    private final SimpleJdbcInsert insertModule;

    // Reuse one mapper for all SELECT queries that return Module rows.
    private final RowMapper<Module> moduleRowMapper = new ModuleRowMapper();

    public ModuleRepository(JdbcTemplate jdbcTemplate) {
        // Spring automatically gives this repository a JdbcTemplate object.
        this.jdbcTemplate = jdbcTemplate;
        this.insertModule = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("modules")
                // study_program_id links this module to one row in study_programs.
                .usingColumns(
                        "study_program_id",
                        "module_number",
                        "name",
                        "credits",
                        "description",
                        "modulebook_id",
                        "module_type",
                        "title_de",
                        "title_en",
                        "coordinator",
                        "applicability",
                        "learning_area",
                        "learning_objectives",
                        "prerequisites",
                        "level",
                        "status",
                        "frequency",
                        "examination_form",
                        "contents",
                        "literature",
                        "additional_notes",
                        "module_weight")
                .usingGeneratedKeyColumns("id");
    }

    // Inserts one Module row and returns the same object with its database id set.
    public Module insert(Module module) {
        Map<String, Object> values = new HashMap<>();

        // Map Java object fields to database column names.
        values.put("study_program_id", module.getStudyProgramId());
        values.put("module_number", module.getModuleNumber());
        values.put("name", module.getName());
        values.put("credits", module.getCredits());
        values.put("description", module.getDescription());
        values.put("modulebook_id", module.getModulebookId());
        values.put("module_type", module.getModuleType());
        values.put("title_de", module.getTitleDe());
        values.put("title_en", module.getTitleEn());
        values.put("coordinator", module.getCoordinator());
        values.put("applicability", module.getApplicability());
        values.put("learning_area", module.getLearningArea());
        values.put("learning_objectives", module.getLearningObjectives());
        values.put("prerequisites", module.getPrerequisites());
        values.put("level", module.getLevel());
        values.put("status", module.getStatus());
        values.put("frequency", module.getFrequency());
        values.put("examination_form", module.getExaminationForm());
        values.put("contents", module.getContents());
        values.put("literature", module.getLiterature());
        values.put("additional_notes", module.getAdditionalNotes());
        values.put("module_weight", module.getModuleWeight());

        // PostgreSQL creates the id, so SimpleJdbcInsert returns the generated key.
        Number newId = insertModule.executeAndReturnKey(values);
        module.setId(newId.longValue());

        return module;
    }

    public Optional<Module> findById(Long id) {
        // The ? placeholder is safely replaced with the id argument by JdbcTemplate.
        List<Module> modules = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM modules WHERE id = ?",
                moduleRowMapper,
                id);

        // A matching row may not exist, so return Optional.empty() in that case.
        return modules.stream().findFirst();
    }

    // Returns all modules in a stable order
    public List<Module> findAll() {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM modules ORDER BY module_number",
                moduleRowMapper);
    }

    public List<Module> findByStudyProgramId(Long studyProgramId) {
        // The selected study program id comes from /api/study-programs/{id}/modules.
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM modules WHERE study_program_id = ? ORDER BY module_number",
                moduleRowMapper,
                studyProgramId);
    }

    // Updates editable fields and returns false when no row with this id exists.
    public boolean update(Long id, Module module) {
        int rowsUpdated = jdbcTemplate.update(
                """
                        UPDATE modules
                        SET study_program_id = ?,
                            module_number = ?,
                            name = ?,
                            credits = ?,
                            description = ?,
                            modulebook_id = ?,
                            module_type = ?,
                            title_de = ?,
                            title_en = ?,
                            coordinator = ?,
                            applicability = ?,
                            learning_area = ?,
                            learning_objectives = ?,
                            prerequisites = ?,
                            level = ?,
                            status = ?,
                            frequency = ?,
                            examination_form = ?,
                            contents = ?,
                            literature = ?,
                            additional_notes = ?,
                            module_weight = ?,
                            updated_at = now()
                        WHERE id = ?
                        """,
                // Keeping study_program_id here allows moving a module to another program if
                // needed later.
                module.getStudyProgramId(),
                module.getModuleNumber(),
                module.getName(),
                module.getCredits(),
                module.getDescription(),
                module.getModulebookId(),
                module.getModuleType(),
                module.getTitleDe(),
                module.getTitleEn(),
                module.getCoordinator(),
                module.getApplicability(),
                module.getLearningArea(),
                module.getLearningObjectives(),
                module.getPrerequisites(),
                module.getLevel(),
                module.getStatus(),
                module.getFrequency(),
                module.getExaminationForm(),
                module.getContents(),
                module.getLiterature(),
                module.getAdditionalNotes(),
                module.getModuleWeight(),
                id);
        return rowsUpdated == 1;
    }

    // Deletes one module and returns false when no row with this id exists.
    public boolean delete(Long id) {
        int rowsDeleted = jdbcTemplate.update(
                "DELETE FROM modules WHERE id = ?",
                id);
        return rowsDeleted == 1;
    }

    // Converts one database row from the modules table into one Java Module object.
    private static class ModuleRowMapper implements RowMapper<Module> {

        // Spring calls this method once for every row returned by a SELECT query.
        @Override
        public Module mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Module(
                    rs.getLong("id"),
                    getLongOrNull(rs, "study_program_id"),
                    rs.getString("module_number"),
                    rs.getString("name"),
                    rs.getBigDecimal("credits"),
                    rs.getString("description"),
                    getLongOrNull(rs, "modulebook_id"),
                    rs.getString("module_type"),
                    rs.getString("title_de"),
                    rs.getString("title_en"),
                    rs.getString("coordinator"),
                    rs.getString("applicability"),
                    rs.getString("learning_area"),
                    rs.getString("learning_objectives"),
                    rs.getString("prerequisites"),
                    rs.getString("level"),
                    rs.getString("status"),
                    rs.getString("frequency"),
                    rs.getString("examination_form"),
                    rs.getString("contents"),
                    rs.getString("literature"),
                    rs.getString("additional_notes"),
                    rs.getBigDecimal("module_weight"),
                    toInstantOrNull(rs, "created_at"),
                    toInstantOrNull(rs, "updated_at"));
        }

        private Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? null : value;
        }

        private Instant toInstantOrNull(ResultSet rs, String columnName) throws SQLException {
            Timestamp timestamp = rs.getTimestamp(columnName);
            return timestamp == null ? null : timestamp.toInstant();
        }
    }

    public Optional<Module> findByStudyProgramIdAndModuleNumber(Long studyProgramId, String moduleNumber) {
        List<Module> modules = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM modules WHERE study_program_id = ? AND module_number = ?",
                moduleRowMapper,
                studyProgramId,
                moduleNumber);

        return modules.stream().findFirst();
    }
}
