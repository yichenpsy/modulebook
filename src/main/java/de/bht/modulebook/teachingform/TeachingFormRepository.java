package de.bht.modulebook.teachingform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TeachingFormRepository {
    private final JdbcTemplate jdbcTemplate;

    public TeachingFormRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TeachingForm> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id, code, label_de, label_en
                FROM teaching_forms
                ORDER BY code
                """,
                this::mapRow
        );
    }

    private TeachingForm mapRow(ResultSet rs, int rowNum) throws SQLException {
        TeachingForm teachingForm = new TeachingForm();
        teachingForm.setId(rs.getLong("id"));
        teachingForm.setCode(rs.getString("code"));
        teachingForm.setLabelDe(rs.getString("label_de"));
        teachingForm.setLabelEn(rs.getString("label_en"));
        return teachingForm;
    }
}
