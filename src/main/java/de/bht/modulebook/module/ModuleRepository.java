package de.bht.modulebook.module;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

// database layer, use JdbcTemplate to run SQL
@Repository
public class ModuleRepository {

    private static final String SELECT_COLUMNS = """
            id, modulebook_id, module_number, title, credits,
            module_type, coordinator, applicability,
            learning_area, learning_objectives, prerequisites, level, module_status, module_comments, frequency,
            examination_form, contents, literature, additional_notes, module_weight, created_at, updated_at,
            created_change_set_id
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
                // modulebook_id links this module to one row in modulebooks.
                .usingColumns(
                        "module_number",
                        "title",
                        "credits",
                        "modulebook_id",
                        "module_type",
                        "coordinator",
                        "applicability",
                        "learning_area",
                        "learning_objectives",
                        "prerequisites",
                        "level",
                        "module_status",
                        "module_comments",
                        "frequency",
                        "examination_form",
                        "contents",
                        "literature",
                        "additional_notes",
                        "module_weight",
                        "created_change_set_id")
                .usingGeneratedKeyColumns("id");
    }

    // Inserts one Module row and returns the same object with its database id set.
    public Module insert(Module module) {
        Map<String, Object> values = new HashMap<>();

        // Map Java object fields to database column names.
        values.put("module_number", module.getModuleNumber());
        values.put("title", module.getTitle());
        values.put("credits", decimalToText(module.getCredits()));
        values.put("modulebook_id", module.getModulebookId());
        values.put("module_type", module.getModuleType());
        values.put("coordinator", module.getCoordinator());
        values.put("applicability", module.getApplicability());
        values.put("learning_area", module.getLearningArea());
        values.put("learning_objectives", module.getLearningObjectives());
        values.put("prerequisites", module.getPrerequisites());
        values.put("level", module.getLevel());
        values.put("module_status", module.getModuleStatus());
        values.put("module_comments", module.getModuleComments());
        values.put("frequency", module.getFrequency());
        values.put("examination_form", module.getExaminationForm());
        values.put("contents", module.getContents());
        values.put("literature", module.getLiterature());
        values.put("additional_notes", module.getAdditionalNotes());
        values.put("module_weight", decimalToText(module.getModuleWeight()));
        values.put("created_change_set_id", module.getCreatedChangeSetId());

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

    public List<Module> findByModulebookId(Long modulebookId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM modules WHERE modulebook_id = ? ORDER BY module_number",
                moduleRowMapper,
                modulebookId);
    }

    public List<Module> findByCreatedChangeSetIds(List<Long> changeSetIds) {
        if (changeSetIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", Collections.nCopies(changeSetIds.size(), "?"));
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM modules WHERE created_change_set_id IN (" + placeholders + ") ORDER BY module_number",
                moduleRowMapper,
                changeSetIds.toArray()
        );
    }

    // Updates editable fields and returns false when no row with this id exists.
    public boolean update(Long id, Module module) {
        int rowsUpdated = jdbcTemplate.update(
                """
                        UPDATE modules
                        SET module_number = ?,
                            title = ?,
                            credits = ?,
                            modulebook_id = ?,
                            module_type = ?,
                            coordinator = ?,
                            applicability = ?,
                            learning_area = ?,
                            learning_objectives = ?,
                            prerequisites = ?,
                            level = ?,
                            module_status = ?,
                            module_comments = ?,
                            frequency = ?,
                            examination_form = ?,
                            contents = ?,
                            literature = ?,
                            additional_notes = ?,
                            module_weight = ?,
                            updated_at = now()
                        WHERE id = ?
                        """,
                module.getModuleNumber(),
                module.getTitle(),
                decimalToText(module.getCredits()),
                module.getModulebookId(),
                module.getModuleType(),
                module.getCoordinator(),
                module.getApplicability(),
                module.getLearningArea(),
                module.getLearningObjectives(),
                module.getPrerequisites(),
                module.getLevel(),
                module.getModuleStatus(),
                module.getModuleComments(),
                module.getFrequency(),
                module.getExaminationForm(),
                module.getContents(),
                module.getLiterature(),
                module.getAdditionalNotes(),
                decimalToText(module.getModuleWeight()),
                id);
        return rowsUpdated == 1;
    }

    private String decimalToText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    // Updates only the module status and returns false when no row with this id exists.
    public boolean updateModuleStatus(Long id, String moduleStatus) {
        int rowsUpdated = jdbcTemplate.update(
                """
                        UPDATE modules
                        SET module_status = ?,
                            updated_at = now()
                        WHERE id = ?
                        """,
                moduleStatus,
                id);
        return rowsUpdated == 1;
    }

    // Physically deletes one module.
    // Other child rows are removed by ON DELETE CASCADE constraints on modules.id.
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
                    getLongOrNull(rs, "modulebook_id"),
                    rs.getString("module_number"),
                    rs.getString("title"),
                    getBigDecimalOrNull(rs, "credits"),
                    rs.getString("module_type"),
                    rs.getString("coordinator"),
                    rs.getString("applicability"),
                    rs.getString("learning_area"),
                    rs.getString("learning_objectives"),
                    rs.getString("prerequisites"),
                    rs.getString("level"),
                    rs.getString("module_status"),
                    rs.getString("module_comments"),
                    rs.getString("frequency"),
                    rs.getString("examination_form"),
                    rs.getString("contents"),
                    rs.getString("literature"),
                    rs.getString("additional_notes"),
                    getBigDecimalOrNull(rs, "module_weight"),
                    toInstantOrNull(rs, "created_at"),
                    toInstantOrNull(rs, "updated_at"),
                    getLongOrNull(rs, "created_change_set_id"));
        }

        private Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? null : value;
        }

        private BigDecimal getBigDecimalOrNull(ResultSet rs, String columnName) throws SQLException {
            String value = rs.getString(columnName);
            if (value == null || value.isBlank()) {
                return null;
            }

            try {
                return new BigDecimal(value.trim().replace(",", "."));
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        private Instant toInstantOrNull(ResultSet rs, String columnName) throws SQLException {
            Timestamp timestamp = rs.getTimestamp(columnName);
            return timestamp == null ? null : timestamp.toInstant();
        }
    }

    public Optional<Module> findByModulebookIdAndModuleNumber(Long modulebookId, String moduleNumber) {
        List<Module> modules = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM modules WHERE modulebook_id = ? AND lower(module_number) = lower(?) ORDER BY id",
                moduleRowMapper,
                modulebookId,
                moduleNumber);

        return modules.stream().findFirst();
    }
}
