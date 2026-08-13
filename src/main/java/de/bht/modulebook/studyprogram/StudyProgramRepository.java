package de.bht.modulebook.studyprogram;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class StudyProgramRepository {

    // JdbcTemplate runs SELECT/UPDATE SQL against the configured PostgreSQL database.
    private final JdbcTemplate jdbcTemplate;

    // SimpleJdbcInsert builds INSERT SQL for study_programs and returns the generated id.
    private final SimpleJdbcInsert insertStudyProgram;

    // Reuse one mapper so every SELECT creates StudyProgram objects in the same way.
    private final RowMapper<StudyProgram> studyProgramRowMapper = new StudyProgramRowMapper();

    public StudyProgramRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertStudyProgram = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("study_programs")
                .usingColumns("name", "year")
                .usingGeneratedKeyColumns("id");
    }

    public StudyProgram insert(StudyProgram studyProgram) {
        Map<String, Object> values = new HashMap<>();

        // Map Java field names to database column names.
        values.put("name", studyProgram.getName());
        values.put("year", studyProgram.getYear());

        // PostgreSQL creates id because study_programs.id is BIGSERIAL.
        Number newId = insertStudyProgram.executeAndReturnKey(values);
        studyProgram.setId(newId.longValue());
        return studyProgram;
    }

    public Optional<StudyProgram> findById(Long id) {
        List<StudyProgram> studyPrograms = jdbcTemplate.query(
                "SELECT id, name, year, created_at, updated_at FROM study_programs WHERE id = ?",
                studyProgramRowMapper,
                id
        );
        return studyPrograms.stream().findFirst();
    }

    public Optional<StudyProgram> findByNameAndYear(String name, Integer year) {
        // name + year is unique in the database, so this returns zero or one row.
        List<StudyProgram> studyPrograms = jdbcTemplate.query(
                "SELECT id, name, year, created_at, updated_at FROM study_programs WHERE name = ? AND year = ?",
                studyProgramRowMapper,
                name,
                year
        );
        return studyPrograms.stream().findFirst();
    }

    public List<StudyProgram> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, year, created_at, updated_at FROM study_programs ORDER BY name, year",
                studyProgramRowMapper
        );
    }

    private static class StudyProgramRowMapper implements RowMapper<StudyProgram> {

        // Converts one database row from study_programs into one Java StudyProgram object.
        @Override
        public StudyProgram mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new StudyProgram(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getInt("year"),
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
