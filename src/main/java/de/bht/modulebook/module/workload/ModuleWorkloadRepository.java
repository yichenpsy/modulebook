package de.bht.modulebook.module.workload;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModuleWorkloadRepository {
    private final JdbcTemplate jdbcTemplate;

    public ModuleWorkloadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ModuleWorkload> findByModuleId(Long moduleId) {
        List<ModuleWorkload> workloads = jdbcTemplate.query(
                """
                SELECT id, module_id, presence_hours, self_study_hours, raw_text
                FROM module_workloads
                WHERE module_id = ?
                """,
                this::mapRow,
                moduleId
        );

        Optional<ModuleWorkload> workload = workloads.stream().findFirst();
        workload.ifPresent(this::loadComponents);
        return workload;
    }

    public void upsert(Long moduleId, ModuleWorkload workload) {
        jdbcTemplate.update(
                """
                INSERT INTO module_workloads (
                    module_id,
                    presence_hours,
                    self_study_hours,
                    raw_text
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT (module_id)
                DO UPDATE SET
                    presence_hours = EXCLUDED.presence_hours,
                    self_study_hours = EXCLUDED.self_study_hours,
                    raw_text = EXCLUDED.raw_text
                """,
                moduleId,
                workload.getPresenceHours(),
                workload.getSelfStudyHours(),
                workload.getRawText()
        );

        Long workloadId = findByModuleId(moduleId)
                .orElseThrow()
                .getId();

        jdbcTemplate.update(
                "DELETE FROM presence_hour_components WHERE module_workload_id = ?",
                workloadId
        );

        for (int sortOrder = 0; sortOrder < workload.getComponents().size(); sortOrder++) {
            PresenceHourComponent component = workload.getComponents().get(sortOrder);
            jdbcTemplate.update(
                    """
                    INSERT INTO presence_hour_components (
                        module_workload_id,
                        teaching_form_id,
                        sws_amount,
                        sort_order
                    )
                    VALUES (?, ?, ?, ?)
                    """,
                    workloadId,
                    component.getTeachingFormId(),
                    component.getSwsAmount(),
                    sortOrder
            );
        }
    }

    public void deleteByModuleId(Long moduleId) {
        jdbcTemplate.update(
                "DELETE FROM module_workloads WHERE module_id = ?",
                moduleId
        );
    }

    private ModuleWorkload mapRow(ResultSet rs, int rowNum) throws SQLException {
        ModuleWorkload workload = new ModuleWorkload();
        workload.setId(rs.getLong("id"));
        workload.setModuleId(rs.getLong("module_id"));
        workload.setPresenceHours(rs.getBigDecimal("presence_hours"));
        workload.setSelfStudyHours(rs.getBigDecimal("self_study_hours"));
        workload.setRawText(rs.getString("raw_text"));
        return workload;
    }

    private void loadComponents(ModuleWorkload workload) {
        List<PresenceHourComponent> components = jdbcTemplate.query(
                """
                SELECT phc.id,
                       phc.teaching_form_id,
                       tf.code AS teaching_form_code,
                       phc.sws_amount
                FROM presence_hour_components phc
                LEFT JOIN teaching_forms tf ON tf.id = phc.teaching_form_id
                WHERE phc.module_workload_id = ?
                ORDER BY phc.sort_order, phc.id
                """,
                this::mapComponentRow,
                workload.getId()
        );

        workload.setComponents(components);
    }

    private PresenceHourComponent mapComponentRow(ResultSet rs, int rowNum) throws SQLException {
        PresenceHourComponent component = new PresenceHourComponent();
        component.setId(rs.getLong("id"));
        component.setTeachingFormId(rs.getLong("teaching_form_id"));
        component.setTeachingFormCode(rs.getString("teaching_form_code"));
        component.setSwsAmount(rs.getBigDecimal("sws_amount"));
        return component;
    }
}
