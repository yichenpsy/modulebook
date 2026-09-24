package de.bht.modulebook.module;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.bht.modulebook.module.dto.ModuleResponse;
import de.bht.modulebook.module.workload.dto.ModuleWorkloadResponse;
import de.bht.modulebook.module.workload.dto.PresenceHourComponentResponse;
import de.bht.modulebook.teachingform.dto.TeachingFormResponse;

@Repository
public class ModuleViewRepository {
    private static final TypeReference<List<TeachingFormResponse>> TEACHING_FORM_LIST =
            new TypeReference<>() {};
    private static final TypeReference<List<PresenceHourComponentResponse>> COMPONENT_LIST =
            new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModuleViewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ModuleResponse> findById(Long id) {
        return jdbcTemplate.query(
                """
                SELECT id, modulebook_id, module_number, name, credits, description,
                       module_type, title_de, title_en, coordinator, applicability,
                       learning_area, learning_objectives, prerequisites, level,
                       module_status, module_comments, frequency, examination_form,
                       contents, literature, additional_notes, module_weight,
                       created_at, updated_at, workload_id, presence_hours,
                       self_study_hours, workload_raw_text,
                       teaching_forms::text AS teaching_forms,
                       presence_hour_components::text AS presence_hour_components
                FROM module_view
                WHERE id = ?
                """,
                this::mapRow,
                id
        ).stream().findFirst();
    }

    private ModuleResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long workloadId = getLongOrNull(rs, "workload_id");
        ModuleWorkloadResponse workload = null;
        if (workloadId != null) {
            workload = new ModuleWorkloadResponse(
                    workloadId,
                    rs.getBigDecimal("presence_hours"),
                    rs.getBigDecimal("self_study_hours"),
                    rs.getString("workload_raw_text"),
                    readJsonList(rs.getString("presence_hour_components"), COMPONENT_LIST)
            );
        }

        return new ModuleResponse(
                rs.getLong("id"),
                getLongOrNull(rs, "modulebook_id"),
                rs.getString("module_number"),
                rs.getString("name"),
                rs.getBigDecimal("credits"),
                rs.getString("description"),
                rs.getString("module_type"),
                rs.getString("title_de"),
                rs.getString("title_en"),
                rs.getString("coordinator"),
                rs.getString("applicability"),
                rs.getString("learning_area"),
                rs.getString("learning_objectives"),
                rs.getString("prerequisites"),
                rs.getString("level"),
                rs.getString("module_status"),
                rs.getString("module_comments"),
                rs.getString("frequency"),
                rs.getString("examination_form"),
                rs.getString("contents"),
                rs.getString("literature"),
                rs.getString("additional_notes"),
                rs.getBigDecimal("module_weight"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                readJsonList(rs.getString("teaching_forms"), TEACHING_FORM_LIST),
                workload
        );
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read module view JSON", exception);
        }
    }

    private Instant getInstant(ResultSet rs, String columnName) throws SQLException {
        Timestamp value = rs.getTimestamp(columnName);
        return value == null ? null : value.toInstant();
    }

    private Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
