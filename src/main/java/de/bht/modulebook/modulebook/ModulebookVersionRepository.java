package de.bht.modulebook.modulebook;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ModulebookVersionRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ModulebookVersion> rowMapper = new ModulebookVersionRowMapper();

    public ModulebookVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ModulebookVersion insert(
            Modulebook modulebook,
            String versionMessage,
            String snapshotJson,
            String sourceType,
            Long sourceChangeSetId,
            LocalDate modulebookReleaseDate,
            LocalDate studyExaminationRegulationsDate
    ) {
        Integer versionNumber = nextVersionNumber(modulebook.getId());
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO modulebook_versions (
                    modulebook_id,
                    study_program_id,
                    version_number,
                    version_message,
                    snapshot_json,
                    source_type,
                    source_change_set_id,
                    modulebook_release_date,
                    study_examination_regulations_date
                )
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                modulebook.getId(),
                modulebook.getStudyProgramId(),
                versionNumber,
                versionMessage,
                snapshotJson,
                sourceType,
                sourceChangeSetId,
                modulebookReleaseDate,
                studyExaminationRegulationsDate
        );
        return findById(id).orElseThrow();
    }

    private Integer nextVersionNumber(Long modulebookId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(version_number), 0) + 1
                FROM modulebook_versions
                WHERE modulebook_id = ?
                """,
                Integer.class,
                modulebookId
        );
    }

    public Optional<ModulebookVersion> findById(Long id) {
        return jdbcTemplate.query(
                        """
                        SELECT modulebook_versions.id,
                               modulebook_versions.modulebook_id,
                               COALESCE(modulebook_versions.study_program_id, modulebooks.study_program_id) AS study_program_id,
                               modulebook_versions.version_number,
                               modulebook_versions.version_message,
                               modulebook_versions.snapshot_json::text AS snapshot_json,
                               modulebook_versions.created_at,
                               modulebook_versions.source_type,
                               modulebook_versions.source_change_set_id,
                               modulebook_versions.modulebook_release_date,
                               modulebook_versions.study_examination_regulations_date
                        FROM modulebook_versions
                        LEFT JOIN modulebooks ON modulebooks.id = modulebook_versions.modulebook_id
                        WHERE modulebook_versions.id = ?
                        """,
                        rowMapper,
                        id
                )
                .stream()
                .findFirst();
    }

    public List<ModulebookVersion> findByStudyProgramId(Long studyProgramId) {
        return jdbcTemplate.query(
                """
                SELECT modulebook_versions.id,
                       modulebook_versions.modulebook_id,
                       COALESCE(modulebook_versions.study_program_id, modulebooks.study_program_id) AS study_program_id,
                       modulebook_versions.version_number,
                       modulebook_versions.version_message,
                       modulebook_versions.snapshot_json::text AS snapshot_json,
                       modulebook_versions.created_at,
                       modulebook_versions.source_type,
                       modulebook_versions.source_change_set_id,
                       modulebook_versions.modulebook_release_date,
                       modulebook_versions.study_examination_regulations_date
                FROM modulebook_versions
                LEFT JOIN modulebooks ON modulebooks.id = modulebook_versions.modulebook_id
                WHERE modulebook_versions.study_program_id = ?
                   OR modulebooks.study_program_id = ?
                ORDER BY modulebook_versions.version_number DESC,
                         modulebook_versions.created_at DESC,
                         modulebook_versions.id DESC
                """,
                rowMapper,
                studyProgramId,
                studyProgramId
        );
    }

    public void deleteByStudyProgramId(Long studyProgramId) {
        jdbcTemplate.update(
                """
                DELETE FROM modulebook_versions
                WHERE study_program_id = ?
                   OR modulebook_id IN (
                       SELECT id
                       FROM modulebooks
                       WHERE study_program_id = ?
                   )
                """,
                studyProgramId,
                studyProgramId
        );
    }

    public boolean delete(Long id) {
        int rowsDeleted = jdbcTemplate.update(
                "DELETE FROM modulebook_versions WHERE id = ?",
                id
        );
        return rowsDeleted == 1;
    }

    private static class ModulebookVersionRowMapper implements RowMapper<ModulebookVersion> {
        @Override
        public ModulebookVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Instant createdAtInstant = createdAt == null ? null : createdAt.toInstant();

            return new ModulebookVersion(
                    rs.getLong("id"),
                    getLongOrNull(rs, "modulebook_id"),
                    rs.getLong("study_program_id"),
                    rs.getInt("version_number"),
                    rs.getString("version_message"),
                    rs.getString("snapshot_json"),
                    createdAtInstant,
                    rs.getString("source_type"),
                    getLongOrNull(rs, "source_change_set_id"),
                    rs.getObject("modulebook_release_date", LocalDate.class),
                    rs.getObject("study_examination_regulations_date", LocalDate.class)
            );
        }

        private Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? null : value;
        }
    }
}
