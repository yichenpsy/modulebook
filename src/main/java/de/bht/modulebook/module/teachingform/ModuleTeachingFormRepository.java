package de.bht.modulebook.module.teachingform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import de.bht.modulebook.teachingform.TeachingForm;

@Repository
public class ModuleTeachingFormRepository {
    private final JdbcTemplate jdbcTemplate;

    public ModuleTeachingFormRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TeachingForm> findByModuleId(Long moduleId) {
        return jdbcTemplate.query(
                """
                SELECT tf.id, tf.code, tf.label_de, tf.label_en
                FROM module_teaching_forms mtf
                JOIN teaching_forms tf ON tf.id = mtf.teaching_form_id
                WHERE mtf.module_id = ?
                ORDER BY tf.code
                """,
                this::mapTeachingFormRow,
                moduleId
        );
    }

    public void replaceForModule(Long moduleId, List<Long> teachingFormIds) {
        jdbcTemplate.update(
                "DELETE FROM module_teaching_forms WHERE module_id = ?",
                moduleId
        );

        for (Long teachingFormId : teachingFormIds) {
            jdbcTemplate.update(
                    """
                    INSERT INTO module_teaching_forms (
                        module_id,
                        teaching_form_id
                    )
                    VALUES (?, ?)
                    """,
                    moduleId,
                    teachingFormId
            );
        }
    }

    private TeachingForm mapTeachingFormRow(ResultSet rs, int rowNum) throws SQLException {
        TeachingForm teachingForm = new TeachingForm();
        teachingForm.setId(rs.getLong("id"));
        teachingForm.setCode(rs.getString("code"));
        teachingForm.setLabelDe(rs.getString("label_de"));
        teachingForm.setLabelEn(rs.getString("label_en"));
        return teachingForm;
    }
}
