package de.bht.modulebook.modulebook;

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
public class ModulebookTagRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ModulebookTag> rowMapper = new ModulebookTagRowMapper();

    public ModulebookTagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ModulebookTag insert(Long studyProgramId, String tagMessage, String snapshotJson) {
        return insert(studyProgramId, tagMessage, snapshotJson, "MANUAL", null);
    }

    public ModulebookTag insert(
            Long studyProgramId,
            String tagMessage,
            String snapshotJson,
            String sourceType,
            Long sourceChangeSetId
    ) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO modulebook_tags (
                    study_program_id,
                    tag_message,
                    snapshot_json,
                    source_type,
                    source_change_set_id
                )
                VALUES (?, ?, CAST(? AS jsonb), ?, ?)
                RETURNING id
                """,
                Long.class,
                studyProgramId,
                tagMessage,
                snapshotJson,
                sourceType,
                sourceChangeSetId
        );
        return findById(id).orElseThrow();
    }

    public Optional<ModulebookTag> findById(Long id) {
        return jdbcTemplate.query(
                        """
                        SELECT id, study_program_id, tag_message, snapshot_json::text AS snapshot_json,
                               created_at, source_type, source_change_set_id
                        FROM modulebook_tags
                        WHERE id = ?
                        """,
                        rowMapper,
                        id
                )
                .stream()
                .findFirst();
    }

    public List<ModulebookTag> findByStudyProgramId(Long studyProgramId) {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_id, tag_message, snapshot_json::text AS snapshot_json,
                       created_at, source_type, source_change_set_id
                FROM modulebook_tags
                WHERE study_program_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                rowMapper,
                studyProgramId
        );
    }

    public void deleteByStudyProgramId(Long studyProgramId) {
        jdbcTemplate.update(
                "DELETE FROM modulebook_tags WHERE study_program_id = ?",
                studyProgramId
        );
    }

    public boolean delete(Long id) {
        int rowsDeleted = jdbcTemplate.update(
                "DELETE FROM modulebook_tags WHERE id = ?",
                id
        );
        return rowsDeleted == 1;
    }

    private static class ModulebookTagRowMapper implements RowMapper<ModulebookTag> {
        @Override
        public ModulebookTag mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Instant createdAtInstant = createdAt == null ? null : createdAt.toInstant();

            return new ModulebookTag(
                    rs.getLong("id"),
                    rs.getLong("study_program_id"),
                    rs.getString("tag_message"),
                    rs.getString("snapshot_json"),
                    createdAtInstant,
                    rs.getString("source_type"),
                    getLongOrNull(rs, "source_change_set_id")
            );
        }

        private Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? null : value;
        }
    }
}
