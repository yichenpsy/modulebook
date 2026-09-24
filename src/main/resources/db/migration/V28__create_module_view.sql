CREATE OR REPLACE VIEW module_view AS
SELECT
    m.id,
    m.modulebook_id,
    m.module_number,
    m.name,
    m.credits,
    m.description,
    m.module_type,
    m.title_de,
    m.title_en,
    m.coordinator,
    m.applicability,
    m.learning_area,
    m.learning_objectives,
    m.prerequisites,
    m.level,
    m.module_status,
    m.module_comments,
    m.frequency,
    m.examination_form,
    m.contents,
    m.literature,
    m.additional_notes,
    m.module_weight,
    m.created_at,
    m.updated_at,
    mw.id AS workload_id,
    mw.presence_hours,
    mw.self_study_hours,
    mw.raw_text AS workload_raw_text,
    COALESCE(teaching_form_data.teaching_forms, '[]'::jsonb) AS teaching_forms,
    COALESCE(component_data.presence_hour_components, '[]'::jsonb) AS presence_hour_components
FROM modules m
LEFT JOIN module_workloads mw
    ON mw.module_id = m.id
LEFT JOIN LATERAL (
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', tf.id,
            'code', tf.code,
            'labelDe', tf.label_de,
            'labelEn', tf.label_en
        )
        ORDER BY tf.code
    ) AS teaching_forms
    FROM module_teaching_forms mtf
    JOIN teaching_forms tf
        ON tf.id = mtf.teaching_form_id
    WHERE mtf.module_id = m.id
) teaching_form_data ON true
LEFT JOIN LATERAL (
    SELECT jsonb_agg(
        jsonb_build_object(
            'id', phc.id,
            'teachingFormId', phc.teaching_form_id,
            'teachingFormCode', tf.code,
            'swsAmount', phc.sws_amount
        )
        ORDER BY phc.sort_order, phc.id
    ) AS presence_hour_components
    FROM presence_hour_components phc
    LEFT JOIN teaching_forms tf
        ON tf.id = phc.teaching_form_id
    WHERE phc.module_workload_id = mw.id
) component_data ON true;
