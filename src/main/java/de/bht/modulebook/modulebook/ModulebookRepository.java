package de.bht.modulebook.modulebook;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ModulebookRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Modulebook> rowMapper = new ModulebookRowMapper();

    public ModulebookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Modulebook insertBlank(Long studyProgramId, String title) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO modulebooks (
                    study_program_id,
                    title,
                    status
                )
                VALUES (?, ?, 'DRAFT')
                RETURNING id
                """,
                Long.class,
                studyProgramId,
                title
        );
        return findById(id).orElseThrow();
    }

    public Optional<Modulebook> findById(Long id) {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_id, title, status,
                       modulebook_release_date, study_examination_regulations_date,
                       created_at, updated_at
                FROM modulebooks
                WHERE id = ?
                """,
                rowMapper,
                id
        ).stream().findFirst();
    }

    public Optional<Modulebook> findByStudyProgramId(Long studyProgramId) {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_id, title, status,
                       modulebook_release_date, study_examination_regulations_date,
                       created_at, updated_at
                FROM modulebooks
                WHERE study_program_id = ?
                """,
                rowMapper,
                studyProgramId
        ).stream().findFirst();
    }

    private static class ModulebookRowMapper implements RowMapper<Modulebook> {
        @Override
        public Modulebook mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Modulebook(
                    rs.getLong("id"),
                    rs.getLong("study_program_id"),
                    rs.getString("title"),
                    rs.getString("status"),
                    rs.getObject("modulebook_release_date", java.time.LocalDate.class),
                    rs.getObject("study_examination_regulations_date", java.time.LocalDate.class),
                    toInstantOrNull(rs, "created_at"),
                    toInstantOrNull(rs, "updated_at")
            );
        }

        private Instant toInstantOrNull(ResultSet rs, String columnName) throws SQLException {
            Timestamp timestamp = rs.getTimestamp(columnName);
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}
