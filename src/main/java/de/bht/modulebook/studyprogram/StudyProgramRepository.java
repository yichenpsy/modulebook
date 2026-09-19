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
                "SELECT id, name, year, created_at, updated_at FROM study_programs WHERE hidden_at IS NULL ORDER BY name, year",
                studyProgramRowMapper
        );
    }

    public void unhide(Long id) {
        jdbcTemplate.update(
                """
                UPDATE study_programs
                SET hidden_at = NULL,
                    hidden_by_change_set_id = NULL,
                    updated_at = now()
                WHERE id = ?
                """,
                id
        );
    }

    public void hideIfNoModules(Long id, Long hiddenByChangeSetId) {
        jdbcTemplate.update(
                """
                UPDATE study_programs
                SET hidden_at = now(),
                    hidden_by_change_set_id = ?,
                    updated_at = now()
                WHERE id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM modules
                      WHERE modules.study_program_id = study_programs.id
                  )
                """,
                hiddenByChangeSetId,
                id
        );
    }

    public boolean delete(Long id) {
        jdbcTemplate.update("DELETE FROM study_program_change_sets WHERE study_program_id = ?", id);

        // modules.study_program_id comes from this study program.
        // Deleting modules first also deletes module child rows such as workloads.
        jdbcTemplate.update("DELETE FROM modules WHERE study_program_id = ?", id);

        // modulebooks are connected through study_examination_regulations.
        jdbcTemplate.update(
                """
                DELETE FROM modulebooks
                WHERE study_examination_regulation_id IN (
                    SELECT id
                    FROM study_examination_regulations
                    WHERE study_program_id = ?
                )
                """,
                id
        );

        jdbcTemplate.update("DELETE FROM study_examination_regulations WHERE study_program_id = ?", id);

        int rowsDeleted = jdbcTemplate.update("DELETE FROM study_programs WHERE id = ?", id);
        return rowsDeleted == 1;
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
