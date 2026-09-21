package de.bht.modulebook.modulebook.changeset;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class ModulebookChangeSetRepository {
    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insert;
    private final RowMapper<ModulebookChangeSet> rowMapper = new ModulebookChangeSetRowMapper();

    public ModulebookChangeSetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("study_program_change_sets")
                .usingColumns("modulebook_id", "source_type", "reason")
                .usingGeneratedKeyColumns("id");
    }

    public ModulebookChangeSet insert(Long modulebookId, String sourceType, String reason) {
        MapSqlParameterSource values = new MapSqlParameterSource()
                .addValue("modulebook_id", modulebookId)
                .addValue("source_type", sourceType)
                .addValue("reason", reason);
        Number id = insert.executeAndReturnKey(values);
        return findById(id.longValue());
    }

    public ModulebookChangeSet findById(Long id) {
        List<ModulebookChangeSet> changeSets = jdbcTemplate.query(
                """
                SELECT id, modulebook_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE id = ?
                """,
                rowMapper,
                id
        );
        return changeSets.stream().findFirst().orElseThrow();
    }

    public List<ModulebookChangeSet> findByModulebookId(Long modulebookId) {
        return jdbcTemplate.query(
                """
                SELECT id, modulebook_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE modulebook_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                rowMapper,
                modulebookId
        );
    }

    public Optional<ModulebookChangeSet> findFirstByModulebookIdAndSourceType(Long modulebookId, String sourceType) {
        return jdbcTemplate.query(
                """
                SELECT id, modulebook_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE modulebook_id = ?
                  AND source_type = ?
                ORDER BY created_at, id
                LIMIT 1
                """,
                rowMapper,
                modulebookId,
                sourceType
        ).stream().findFirst();
    }

    public List<ModulebookChangeSet> findActiveFromInclusive(ModulebookChangeSet target) {
        return jdbcTemplate.query(
                """
                SELECT id, modulebook_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE modulebook_id = ?
                  AND discarded_at IS NULL
                  AND (
                      created_at > ?
                      OR (created_at = ? AND id >= ?)
                  )
                ORDER BY created_at, id
                """,
                rowMapper,
                target.getModulebookId(),
                Timestamp.from(target.getCreatedAt()),
                Timestamp.from(target.getCreatedAt()),
                target.getId()
        );
    }

    public List<ModulebookChangeSet> findAfter(ModulebookChangeSet changeSet) {
        return jdbcTemplate.query(
                """
                SELECT id, modulebook_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE modulebook_id = ?
                  AND (
                      created_at > ?
                      OR (created_at = ? AND id > ?)
                  )
                ORDER BY created_at, id
                """,
                rowMapper,
                changeSet.getModulebookId(),
                Timestamp.from(changeSet.getCreatedAt()),
                Timestamp.from(changeSet.getCreatedAt()),
                changeSet.getId()
        );
    }

    public void markDiscarded(List<Long> changeSetIds, Long restoreChangeSetId) {
        if (changeSetIds.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(changeSetIds.size(), "?"));
        Object[] arguments = new Object[changeSetIds.size() + 1];
        arguments[0] = restoreChangeSetId;
        for (int index = 0; index < changeSetIds.size(); index++) {
            arguments[index + 1] = changeSetIds.get(index);
        }

        jdbcTemplate.update(
                "UPDATE study_program_change_sets "
                        + "SET discarded_at = now(), discarded_by_change_set_id = ? "
                        + "WHERE id IN (" + placeholders + ")",
                arguments
        );
    }

    public void deleteByIds(List<Long> changeSetIds) {
        if (changeSetIds.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(changeSetIds.size(), "?"));
        jdbcTemplate.update(
                "DELETE FROM study_program_change_sets WHERE id IN (" + placeholders + ")",
                changeSetIds.toArray()
        );
    }

    private static class ModulebookChangeSetRowMapper implements RowMapper<ModulebookChangeSet> {
        @Override
        public ModulebookChangeSet mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Instant createdAtInstant = createdAt == null ? null : createdAt.toInstant();

            return new ModulebookChangeSet(
                    rs.getLong("id"),
                    rs.getLong("modulebook_id"),
                    rs.getString("source_type"),
                    rs.getString("reason"),
                    createdAtInstant,
                    toInstantOrNull(rs, "discarded_at"),
                    getLongOrNull(rs, "discarded_by_change_set_id")
            );
        }

        private Instant toInstantOrNull(ResultSet rs, String columnName) throws SQLException {
            Timestamp timestamp = rs.getTimestamp(columnName);
            return timestamp == null ? null : timestamp.toInstant();
        }

        private Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? null : value;
        }
    }
}
