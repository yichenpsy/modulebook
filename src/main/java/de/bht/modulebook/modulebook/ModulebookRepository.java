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
public class ModulebookRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Modulebook> rowMapper = new ModulebookRowMapper();

    public ModulebookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Modulebook insert(String studyProgramName, String degreeType) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO modulebooks (
                    study_program_name,
                    degree_type
                )
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                studyProgramName,
                degreeType
        );
        return findById(id).orElseThrow();
    }

    public Optional<Modulebook> findById(Long id) {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_name, degree_type, created_at, updated_at
                FROM modulebooks
                WHERE id = ?
                """,
                rowMapper,
                id
        ).stream().findFirst();
    }

    public Optional<Modulebook> findByStudyProgramNameAndDegreeType(String studyProgramName, String degreeType) {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_name, degree_type, created_at, updated_at
                FROM modulebooks
                WHERE study_program_name = ? AND degree_type = ?
                """,
                rowMapper,
                studyProgramName,
                degreeType
        ).stream().findFirst();
    }

    public List<Modulebook> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id, study_program_name, degree_type, created_at, updated_at
                FROM modulebooks
                ORDER BY study_program_name, degree_type
                """,
                rowMapper
        );
    }

    public boolean delete(Long id) {
        int rowsDeleted = jdbcTemplate.update("DELETE FROM modulebooks WHERE id = ?", id);
        return rowsDeleted == 1;
    }

    private static class ModulebookRowMapper implements RowMapper<Modulebook> {
        @Override
        public Modulebook mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Modulebook(
                    rs.getLong("id"),
                    rs.getString("study_program_name"),
                    rs.getString("degree_type"),
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
