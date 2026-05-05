-- V3: Custom operator template library (hot-pluggable, DB-persisted)
CREATE TABLE qc_operator_template (
    id              BIGSERIAL       PRIMARY KEY,
    code            VARCHAR(64)     NOT NULL UNIQUE,
    name            VARCHAR(200)    NOT NULL,
    description     TEXT,
    -- JSON array of parameter names, e.g. ["birthDateField","refDateField","ageField"]
    parameter_names JSONB           NOT NULL DEFAULT '[]',
    -- DSL body; use {"$ref":"paramName"} wherever a field name should be substituted
    body_dsl        JSONB           NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Seed: ageConsistent — 出生日期与年龄一致性校验
INSERT INTO qc_operator_template
    (code, name, description, parameter_names, body_dsl)
VALUES (
    'ageConsistent',
    '出生日期与年龄一致',
    '校验填报年龄与出生日期推算的周岁是否一致（误差 0 年）。参数：birthDateField / refDateField / ageField',
    '["birthDateField","refDateField","ageField"]',
    '{
      "op": "eq",
      "field": {"$ref": "ageField"},
      "rhs": {"op": "ageYears", "birthDate": {"$ref": "birthDateField"}, "refDate": {"$ref": "refDateField"}}
    }'
);
