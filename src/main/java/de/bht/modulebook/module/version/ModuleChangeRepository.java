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
public class ModuleChangeRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ModuleChange> rowMapper = new ModuleChangeRowMapper();

    public ModuleChangeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Long changeSetId, Long moduleId, String changePath, String oldValueJson, String newValueJson) {
        jdbcTemplate.update(
                """
                INSERT INTO module_changes (
                    change_set_id,
                    module_id,
                    change_path,
                    old_value,
                    new_value
                )
                VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """,
                changeSetId,
                moduleId,
                changePath,
                oldValueJson,
                newValueJson
        );
    }

    public List<ModuleChange> findByChangeSetId(Long changeSetId) {
        return jdbcTemplate.query(
                """
                SELECT module_changes.id,
                       module_changes.change_set_id,
                       module_changes.module_id,
                       module_changes.change_path,
                       module_changes.old_value::text AS old_value,
                       module_changes.new_value::text AS new_value,
                       module_changes.created_at
                FROM module_changes
                WHERE change_set_id = ?
                ORDER BY id
                """,
                rowMapper,
                changeSetId
        );
    }

    public List<ModuleChange> findByChangeSetIds(List<Long> changeSetIds) {
        if (changeSetIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", Collections.nCopies(changeSetIds.size(), "?"));
        return jdbcTemplate.query(
                """
                SELECT module_changes.id,
                       module_changes.change_set_id,
                       module_changes.module_id,
                       module_changes.change_path,
                       module_changes.old_value::text AS old_value,
                       module_changes.new_value::text AS new_value,
                       module_changes.created_at
                FROM module_changes
                JOIN modulebook_change_sets ON modulebook_change_sets.id = module_changes.change_set_id
                WHERE module_changes.change_set_id IN (
                """ + placeholders + """
                )
                ORDER BY modulebook_change_sets.created_at, modulebook_change_sets.id, module_changes.id
                """,
                rowMapper,
                changeSetIds.toArray()
        );
    }

    public List<ModuleChange> findByModuleId(Long moduleId) {
        return jdbcTemplate.query(
                """
                SELECT id, change_set_id, module_id, change_path,
                       old_value::text AS old_value,
                       new_value::text AS new_value,
                       created_at
                FROM module_changes
                WHERE module_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                rowMapper,
                moduleId
        );
    }

    private static class ModuleChangeRowMapper implements RowMapper<ModuleChange> {
        @Override
        public ModuleChange mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Instant createdAtInstant = createdAt == null ? null : createdAt.toInstant();

            return new ModuleChange(
                    rs.getLong("id"),
                    rs.getLong("change_set_id"),
                    getLongOrNull(rs, "module_id"),
                    rs.getString("change_path"),
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
