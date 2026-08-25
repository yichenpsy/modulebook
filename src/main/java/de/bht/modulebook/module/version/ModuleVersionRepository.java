package de.bht.modulebook.module.version;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ModuleVersionRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ModuleVersion> rowMapper = new ModuleVersionRowMapper();

    public ModuleVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int nextVersionNumber(Long moduleId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) FROM module_versions WHERE module_id = ?",
                Integer.class,
                moduleId
        );
        return max + 1;
    }

    public void insert(Long moduleId, int versionNumber, String snapshotJson, String reason) {
        jdbcTemplate.update(
                """
                INSERT INTO module_versions (module_id, version_number, snapshot_json, reason)
                VALUES (?, ?, CAST(? AS jsonb), ?)
                """,
                moduleId,
                versionNumber,
                snapshotJson,
                reason
        );
    }

    public void updateReason(Long moduleId, int versionNumber, String reason) {
        jdbcTemplate.update(
                """
                UPDATE module_versions
                SET reason = ?
                WHERE module_id = ? AND version_number = ?
                """,
                reason,
                moduleId,
                versionNumber
        );
    }

    public List<ModuleVersion> findByModuleId(Long moduleId) {
        return jdbcTemplate.query(
                """
                SELECT id, module_id, version_number, snapshot_json::text AS snapshot_json, reason, created_at
                FROM module_versions
                WHERE module_id = ?
                ORDER BY version_number DESC
                """,
                rowMapper,
                moduleId
        );
    }

    public Optional<ModuleVersion> findByModuleIdAndVersionNumber(Long moduleId, int versionNumber) {
        List<ModuleVersion> versions = jdbcTemplate.query(
                """
                SELECT id, module_id, version_number, snapshot_json::text AS snapshot_json, reason, created_at
                FROM module_versions
                WHERE module_id = ? AND version_number = ?
                """,
                rowMapper,
                moduleId,
                versionNumber
        );
        return versions.stream().findFirst();
    }

    private static class ModuleVersionRowMapper implements RowMapper<ModuleVersion> {
        @Override
        public ModuleVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Instant createdAtInstant = createdAt == null ? null : createdAt.toInstant();

            return new ModuleVersion(
                    rs.getLong("id"),
                    rs.getLong("module_id"),
                    rs.getInt("version_number"),
                    rs.getString("snapshot_json"),
                    rs.getString("reason"),
                    createdAtInstant
            );
        }
    }
}
