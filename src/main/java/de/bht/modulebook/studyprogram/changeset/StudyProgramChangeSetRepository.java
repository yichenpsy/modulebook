package de.bht.modulebook.studyprogram.changeset;

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
public class StudyProgramChangeSetRepository {
    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insert;
    private final RowMapper<StudyProgramChangeSet> rowMapper = new StudyProgramChangeSetRowMapper();

    public StudyProgramChangeSetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("study_program_change_sets")
                .usingColumns("study_program_id", "source_type", "reason")
                .usingGeneratedKeyColumns("id");
    }

    public StudyProgramChangeSet insert(Long studyProgramId, String sourceType, String reason) {
        MapSqlParameterSource values = new MapSqlParameterSource()
                .addValue("study_program_id", studyProgramId)
                .addValue("source_type", sourceType)
                .addValue("reason", reason);
        Number id = insert.executeAndReturnKey(values);
        return findById(id.longValue());
    }

    public StudyProgramChangeSet findById(Long id) {
        List<StudyProgramChangeSet> changeSets = jdbcTemplate.query(
                """
                SELECT id, study_program_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE id = ?
                """,
                rowMapper,
                id
        );
        return changeSets.stream().findFirst().orElseThrow();
    }

    public List<StudyProgramChangeSet> findByStudyProgramId(Long studyProgramId) {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE study_program_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                rowMapper,
                studyProgramId
        );
    }

    public Optional<StudyProgramChangeSet> findFirstByStudyProgramIdAndSourceType(Long studyProgramId, String sourceType) {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE study_program_id = ?
                  AND source_type = ?
                ORDER BY created_at, id
                LIMIT 1
                """,
                rowMapper,
                studyProgramId,
                sourceType
        ).stream().findFirst();
    }

    public List<StudyProgramChangeSet> findActiveFromInclusive(StudyProgramChangeSet target) {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE study_program_id = ?
                  AND discarded_at IS NULL
                  AND (
                      created_at > ?
                      OR (created_at = ? AND id >= ?)
                  )
                ORDER BY created_at, id
                """,
                rowMapper,
                target.getStudyProgramId(),
                Timestamp.from(target.getCreatedAt()),
                Timestamp.from(target.getCreatedAt()),
                target.getId()
        );
    }

    public List<StudyProgramChangeSet> findAfter(StudyProgramChangeSet changeSet) {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_id, source_type, reason, created_at,
                       discarded_at, discarded_by_change_set_id
                FROM study_program_change_sets
                WHERE study_program_id = ?
                  AND (
                      created_at > ?
                      OR (created_at = ? AND id > ?)
                  )
                ORDER BY created_at, id
                """,
                rowMapper,
                changeSet.getStudyProgramId(),
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

    private static class StudyProgramChangeSetRowMapper implements RowMapper<StudyProgramChangeSet> {
        @Override
        public StudyProgramChangeSet mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Instant createdAtInstant = createdAt == null ? null : createdAt.toInstant();

            return new StudyProgramChangeSet(
                    rs.getLong("id"),
                    rs.getLong("study_program_id"),
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
