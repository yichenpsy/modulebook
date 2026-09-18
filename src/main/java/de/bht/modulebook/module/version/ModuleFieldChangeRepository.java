package de.bht.modulebook.module.version;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Collections;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ModuleFieldChangeRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ModuleFieldChange> rowMapper = new ModuleFieldChangeRowMapper();

    public ModuleFieldChangeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Long changeSetId, Long moduleId, String fieldPath, String oldValueJson, String newValueJson) {
        jdbcTemplate.update(
                """
                INSERT INTO module_field_changes (
                    change_set_id,
                    module_id,
                    field_path,
                    old_value,
                    new_value
                )
                VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """,
                changeSetId,
                moduleId,
                fieldPath,
                oldValueJson,
                newValueJson
        );
    }

    public List<ModuleFieldChange> findByChangeSetId(Long changeSetId) {
        return jdbcTemplate.query(
                """
                SELECT module_field_changes.id,
                       module_field_changes.change_set_id,
                       module_field_changes.module_id,
                       module_field_changes.field_path,
                       module_field_changes.old_value::text AS old_value,
                       module_field_changes.new_value::text AS new_value,
                       module_field_changes.created_at
                FROM module_field_changes
                WHERE change_set_id = ?
                ORDER BY id
                """,
                rowMapper,
                changeSetId
        );
    }

    public List<ModuleFieldChange> findByChangeSetIds(List<Long> changeSetIds) {
        if (changeSetIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", Collections.nCopies(changeSetIds.size(), "?"));
        return jdbcTemplate.query(
                """
                SELECT module_field_changes.id,
                       module_field_changes.change_set_id,
                       module_field_changes.module_id,
                       module_field_changes.field_path,
                       module_field_changes.old_value::text AS old_value,
                       module_field_changes.new_value::text AS new_value,
                       module_field_changes.created_at
                FROM module_field_changes
                JOIN study_program_change_sets ON study_program_change_sets.id = module_field_changes.change_set_id
                WHERE module_field_changes.change_set_id IN (
                """ + placeholders + """
                )
                ORDER BY study_program_change_sets.created_at, study_program_change_sets.id, module_field_changes.id
                """,
                rowMapper,
                changeSetIds.toArray()
        );
    }

    public List<ModuleFieldChange> findByModuleId(Long moduleId) {
        return jdbcTemplate.query(
                """
                SELECT id, change_set_id, module_id, field_path,
                       old_value::text AS old_value,
                       new_value::text AS new_value,
                       created_at
                FROM module_field_changes
                WHERE module_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                rowMapper,
                moduleId
        );
    }

    private static class ModuleFieldChangeRowMapper implements RowMapper<ModuleFieldChange> {
        @Override
        public ModuleFieldChange mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Instant createdAtInstant = createdAt == null ? null : createdAt.toInstant();

            return new ModuleFieldChange(
                    rs.getLong("id"),
                    rs.getLong("change_set_id"),
                    getLongOrNull(rs, "module_id"),
                    rs.getString("field_path"),
                    rs.getString("old_value"),
                    rs.getString("new_value"),
                    createdAtInstant
            );
        }

        private Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? null : value;
        }
    }
}
