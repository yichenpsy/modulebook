package de.bht.modulebook.studyprogram.changeset;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

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
                SELECT id, study_program_id, source_type, reason, created_at
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
                SELECT id, study_program_id, source_type, reason, created_at
                FROM study_program_change_sets
                WHERE study_program_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                rowMapper,
                studyProgramId
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
                    createdAtInstant
            );
        }
    }
}
