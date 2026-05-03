-- ============================================================================
-- V1__init_schema.sql -- DeepAudit MVP initial schema
--
-- Five core tables (qc_rule, medical_record_main, medical_record_extra,
-- check_result, icd_dict) plus two seed quality-control rules (R001, R002)
-- referenced by the MVP plan §4.3.
--
-- Design notes:
--
--   1. Vector (ivfflat) indexes are intentionally NOT created in this
--      migration. pgvector ivfflat clusters are computed at index-creation
--      time, so building the index over an empty (or 2-row) table produces
--      poor centroids. Vector indexes are added in a follow-up migration
--      after the ICD dictionary bulk-load and after qc_rule embeddings have
--      been backfilled. See plan §4.2 (revised) and §4.6.
--
--   2. All timestamps use TIMESTAMPTZ. Medical-record events may originate
--      from different time zones (cross-hospital PDF imports); naive
--      TIMESTAMP would silently lose that distinction.
--
--   3. The pgvector extension is normally created by
--      postgres/init/00-extensions.sql (mounted at
--      /docker-entrypoint-initdb.d/) on first DB boot. The IF NOT EXISTS
--      guard below makes this migration self-sufficient on a database that
--      was not bootstrapped via docker-compose -- managed PG, restored
--      dump, fresh CI database, etc.
--
--   4. medical_record_main carries soft-delete (deleted_at) because PHI
--      should never be hard-deleted; partial indexes filter on it.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ----------------------------------------------------------------------------
-- 1. qc_rule -- quality-control rules with JSON DSL expression
-- ----------------------------------------------------------------------------
CREATE TABLE qc_rule (
    id                       BIGSERIAL    PRIMARY KEY,
    code                     VARCHAR(64)  NOT NULL UNIQUE,
    name                     VARCHAR(200) NOT NULL,
    description              TEXT,
    dimension                VARCHAR(20)  NOT NULL CHECK (dimension IN
                                ('completeness','logic','standardization','consistency')),
    severity                 VARCHAR(20)  NOT NULL CHECK (severity IN
                                ('mandatory','deduction','hint')),
    expression               JSONB        NOT NULL,
    error_message_template   TEXT         NOT NULL,
    enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    version                  INT          NOT NULL DEFAULT 1,
    effective_from           DATE,
    effective_to             DATE,
    description_embedding    VECTOR(1024),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at               TIMESTAMPTZ
);

CREATE INDEX idx_qc_rule_enabled    ON qc_rule(enabled)   WHERE deleted_at IS NULL;
CREATE INDEX idx_qc_rule_dimension  ON qc_rule(dimension) WHERE deleted_at IS NULL;

-- ----------------------------------------------------------------------------
-- 2. medical_record_main -- curated 30 fields (covers seed rules R001, R002)
-- ----------------------------------------------------------------------------
CREATE TABLE medical_record_main (
    id                      BIGSERIAL    PRIMARY KEY,

    -- Basic identity (6)
    record_no               VARCHAR(64)  NOT NULL,
    name                    VARCHAR(100),
    gender                  VARCHAR(10),
    birth_date              DATE,
    age                     INT,
    id_card_masked          VARCHAR(32),

    -- Admission / discharge (7)
    admission_date          DATE,
    discharge_date          DATE,
    length_of_stay          INT,
    admission_dept          VARCHAR(100),
    discharge_dept          VARCHAR(100),
    admission_route         VARCHAR(50),
    discharge_status        VARCHAR(50),

    -- Diagnoses (5)
    main_diagnosis_code     VARCHAR(32),
    main_diagnosis_name     VARCHAR(200),
    main_diagnosis_icd_ver  VARCHAR(20),
    other_diagnosis_count   INT,
    pathological_diagnosis  VARCHAR(200),

    -- Operations (5)
    main_operation_code     VARCHAR(32),
    main_operation_name     VARCHAR(200),
    operation_date          DATE,
    operator                VARCHAR(100),
    anesthesia_method       VARCHAR(50),

    -- Cost categories (4)
    total_cost              NUMERIC(12,2),
    drug_cost               NUMERIC(12,2),
    operation_cost          NUMERIC(12,2),
    medical_service_cost    NUMERIC(12,2),

    -- Source / extraction metadata (3)
    source_hospital         VARCHAR(200),
    source_pdf_path         VARCHAR(500),
    extraction_confidence   NUMERIC(3,2),

    -- Workflow status (1)
    status                  VARCHAR(20)  NOT NULL DEFAULT 'draft'
                            CHECK (status IN ('draft','confirmed','checked')),

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,

    -- Same external record number from the same source must be unique --
    -- prevents silent duplicate imports when the operator re-uploads a PDF.
    -- source_hospital may be NULL during early ingestion; (NULL, X) is
    -- treated as distinct from (NULL, X) by Postgres NULLS DISTINCT default,
    -- which is acceptable for MVP (operators can fix metadata before re-check).
    CONSTRAINT uq_mrm_source_record UNIQUE (source_hospital, record_no)
);

CREATE INDEX idx_mrm_record_no  ON medical_record_main(record_no) WHERE deleted_at IS NULL;
CREATE INDEX idx_mrm_status     ON medical_record_main(status)    WHERE deleted_at IS NULL;

-- ----------------------------------------------------------------------------
-- 3. medical_record_extra -- JSONB sidecar for fields outside the curated 30
--
-- Note (DSL limitation): the rule DSL `field` operand is a flat field name
-- (e.g. "mainOperationCode"). MVP rules cannot reference nested JSONB paths
-- inside extra_fields. Plan §4.3 documents this constraint; rules that need
-- a field stored here must wait until the DSL path operand is implemented.
-- ----------------------------------------------------------------------------
CREATE TABLE medical_record_extra (
    id              BIGSERIAL    PRIMARY KEY,
    record_id       BIGINT       NOT NULL UNIQUE
                     REFERENCES medical_record_main(id) ON DELETE CASCADE,
    extra_fields    JSONB        NOT NULL DEFAULT '{}'::jsonb
);

-- ----------------------------------------------------------------------------
-- 4. check_result -- one row per rule hit, with write-time snapshots
-- ----------------------------------------------------------------------------
CREATE TABLE check_result (
    id                      BIGSERIAL    PRIMARY KEY,
    record_id               BIGINT       NOT NULL
                            REFERENCES medical_record_main(id) ON DELETE CASCADE,
    rule_id                 BIGINT       NOT NULL REFERENCES qc_rule(id),

    -- Write-time snapshots so later rule edits don't pollute history.
    rule_code_snapshot      VARCHAR(64)  NOT NULL,
    rule_name_snapshot      VARCHAR(200) NOT NULL,
    rule_severity_snapshot  VARCHAR(20)  NOT NULL,
    rule_dimension_snapshot VARCHAR(20)  NOT NULL,

    -- Hit details
    field_path              VARCHAR(200),
    field_value_snapshot    TEXT,
    hit_message             TEXT         NOT NULL,

    -- LLM explanation cache (lazy: filled on first "view detail" click)
    llm_explanation         TEXT,
    llm_explained_at        TIMESTAMPTZ,

    -- User disposition
    status                  VARCHAR(20)  NOT NULL DEFAULT 'open'
                            CHECK (status IN ('open','acknowledged','false_positive')),

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cr_record  ON check_result(record_id);
CREATE INDEX idx_cr_rule    ON check_result(rule_id);
CREATE INDEX idx_cr_status  ON check_result(status);

-- ----------------------------------------------------------------------------
-- 5. icd_dict -- ICD-10 / ICD-9-CM-3 reference data with semantic embedding
-- ----------------------------------------------------------------------------
CREATE TABLE icd_dict (
    id              BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(500) NOT NULL,
    category        VARCHAR(20)  NOT NULL CHECK (category IN ('icd10','icd9cm3')),
    version         VARCHAR(20)  NOT NULL,
    name_embedding  VECTOR(1024),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    UNIQUE (code, category, version)
);

CREATE INDEX idx_icd_category ON icd_dict(category);

-- ----------------------------------------------------------------------------
-- Seed: R001 (completeness) and R002 (logic) -- MVP §4.3 reference rules.
--
-- description_embedding is intentionally NULL at seed time; the backend
-- backfills the embedding via text-embedding-v3 on first L2 (rule-dedup)
-- call or via an admin endpoint. Storing a stale or hand-fabricated vector
-- here would defeat the dedup purpose.
-- ----------------------------------------------------------------------------
INSERT INTO qc_rule (code, name, description, dimension, severity, expression, error_message_template)
VALUES
('R001',
 '手术信息完整性检查',
 '当病案存在主手术编码时，手术医生与手术日期不能为空。',
 'completeness',
 'mandatory',
 '{
   "when":   { "op": "notNull", "field": "mainOperationCode" },
   "assert": {
     "op": "and",
     "args": [
       { "op": "notNull", "field": "operator" },
       { "op": "notNull", "field": "operationDate" }
     ]
   }
 }'::jsonb,
 '病案存在主手术编码 {{mainOperationCode}}，但手术医生或手术日期未填写'),
('R002',
 '入出院日期与住院天数逻辑校验',
 '入院日期必须早于出院日期，且住院天数 = 出院日期 - 入院日期。',
 'logic',
 'mandatory',
 '{
   "op": "and",
   "args": [
     { "op": "dateBefore", "field": "admissionDate", "rhs": { "field": "dischargeDate" } },
     { "op": "eq", "field": "lengthOfStay",
       "rhs": { "op": "dateDiffDays", "from": "admissionDate", "to": "dischargeDate" } }
   ]
 }'::jsonb,
 '入院日期 {{admissionDate}} 与出院日期 {{dischargeDate}} 不一致或住院天数 {{lengthOfStay}} 计算错误');
