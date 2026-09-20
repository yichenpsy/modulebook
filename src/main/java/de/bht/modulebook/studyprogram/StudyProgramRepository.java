package de.bht.modulebook.studyprogram;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
                .usingColumns(
                        "name",
                        "degree_type",
                        "modulebook_release_date",
                        "study_examination_regulations_date"
                )
                .usingGeneratedKeyColumns("id");
    }

    public StudyProgram insert(StudyProgram studyProgram) {
        Map<String, Object> values = new HashMap<>();

        // Map Java field names to database column names.
        values.put("name", studyProgram.getName());
        values.put("degree_type", studyProgram.getDegreeType());
        values.put("modulebook_release_date", studyProgram.getModulebookReleaseDate());
        values.put("study_examination_regulations_date", studyProgram.getStudyExaminationRegulationsDate());

        // PostgreSQL creates id because study_programs.id is BIGSERIAL.
        Number newId = insertStudyProgram.executeAndReturnKey(values);
        studyProgram.setId(newId.longValue());
        return studyProgram;
    }

    public Optional<StudyProgram> findById(Long id) {
        List<StudyProgram> studyPrograms = jdbcTemplate.query(
                """
                SELECT id, name, degree_type, modulebook_release_date,
                       study_examination_regulations_date, created_at, updated_at
                FROM study_programs
                WHERE id = ?
                """,
                studyProgramRowMapper,
                id
        );
        return studyPrograms.stream().findFirst();
    }

    public Optional<StudyProgram> findByNameAndDegreeType(String name, String degreeType) {
        // Only visible study programs should block creating another program with the same name/type.
        // Hidden rows are old restore/import bookkeeping and are not shown to the user.
        List<StudyProgram> studyPrograms = jdbcTemplate.query(
                """
                SELECT id, name, degree_type, modulebook_release_date,
                       study_examination_regulations_date, created_at, updated_at
                FROM study_programs
                WHERE name = ? AND degree_type = ? AND hidden_at IS NULL
                """,
                studyProgramRowMapper,
                name,
                degreeType
        );
        return studyPrograms.stream().findFirst();
    }

    public StudyProgram updateMetadata(StudyProgram studyProgram) {
        jdbcTemplate.update(
                """
                UPDATE study_programs
                SET modulebook_release_date = ?,
                    study_examination_regulations_date = ?,
                    updated_at = now()
                WHERE id = ?
                """,
                studyProgram.getModulebookReleaseDate(),
                studyProgram.getStudyExaminationRegulationsDate(),
                studyProgram.getId()
        );
        return findById(studyProgram.getId()).orElseThrow();
    }

    public List<StudyProgram> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id, name, degree_type, modulebook_release_date,
                       study_examination_regulations_date, created_at, updated_at
                FROM study_programs
                WHERE hidden_at IS NULL
                ORDER BY name, degree_type
                """,
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
                    rs.getString("degree_type"),
                    toLocalDateOrNull(rs, "modulebook_release_date"),
                    toLocalDateOrNull(rs, "study_examination_regulations_date"),
                    toInstantOrNull(rs, "created_at"),
                    toInstantOrNull(rs, "updated_at")
            );
        }

        private LocalDate toLocalDateOrNull(ResultSet rs, String columnName) throws SQLException {
            return rs.getObject(columnName, LocalDate.class);
        }

        private Instant toInstantOrNull(ResultSet rs, String columnName) throws SQLException {
            Timestamp timestamp = rs.getTimestamp(columnName);
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}
