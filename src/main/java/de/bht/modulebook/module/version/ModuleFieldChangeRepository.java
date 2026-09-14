package de.bht.modulebook.module.version;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

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
                SELECT id, change_set_id, module_id, field_path,
                       old_value::text AS old_value,
                       new_value::text AS new_value,
                       created_at
                FROM module_field_changes
                WHERE change_set_id = ?
                ORDER BY id
                """,
                rowMapper,
                changeSetId
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
                    rs.getLong("module_id"),
                    rs.getString("field_path"),
                    rs.getString("old_value"),
                    rs.getString("new_value"),
                    createdAtInstant
            );
        }
    }
}
