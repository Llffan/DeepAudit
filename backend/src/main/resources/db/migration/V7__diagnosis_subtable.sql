-- ============================================================================
-- V7: 出院诊断子表 + main 表主诊属性扩展
--
-- 一份病案 1 条主要诊断 + 0~N 条其他诊断。HQMS 国标 xlsx 用 QTZD1-40 反规范
-- 化的 40 个槽位表达，PG 这边回到规范化子表。
--
-- 双写策略：
--   - main 表保留 main_diagnosis_code/_name/_icd_ver 三列，新增本次的 3 列
--     (main_admission_condition/_discharge_condition/_note) 让主诊的全部 6
--     字段都能平铺在 main 表 — 现有 R001/R004/R005 规则继续可用扁平字段引用，
--     无需扩 DSL 跨表能力。
--   - 子表 medical_record_diagnosis 同时装"主诊 + 所有其他诊断"，service 层
--     在 save 时把 main 表主诊字段合成 (diag_type='main', seq_no=1) 一行写入
--     子表，保证按诊断查询/统计的能力（"哪些病案诊断含 J18"、"按 ICD 分类
--     统计"等）走子表索引而非主表 LIKE。
-- ============================================================================

ALTER TABLE medical_record_main
    ADD COLUMN main_admission_condition VARCHAR(20),
    ADD COLUMN main_discharge_condition VARCHAR(20),
    ADD COLUMN main_note                TEXT;

CREATE TABLE medical_record_diagnosis (
    id                   BIGSERIAL    PRIMARY KEY,
    record_id            BIGINT       NOT NULL
                                      REFERENCES medical_record_main(id) ON DELETE CASCADE,

    -- 'main' 主要诊断 / 'other' 其他诊断
    diag_type            VARCHAR(20)  NOT NULL CHECK (diag_type IN ('main','other')),
    seq_no               INT          NOT NULL DEFAULT 1,

    diagnosis_name       VARCHAR(200) NOT NULL,
    diagnosis_code       VARCHAR(32),                       -- ICD-10
    icd_version          VARCHAR(20),                       -- 'ICD-10' / 'ICD-9-CM-3'

    -- HQMS RC014 入院病况：有 / 临床未确定 / 情况不明 / 无
    admission_condition  VARCHAR(20),
    -- HQMS RC015 出院情况：治愈 / 好转 / 未愈 / 死亡 / 其他
    discharge_condition  VARCHAR(20),

    note                 TEXT,                              -- 诊断备注

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_mrd_seq UNIQUE (record_id, diag_type, seq_no)
);

CREATE INDEX idx_mrd_record_type ON medical_record_diagnosis (record_id, diag_type, seq_no);
CREATE INDEX idx_mrd_code        ON medical_record_diagnosis (diagnosis_code) WHERE diagnosis_code IS NOT NULL;

-- 部分唯一索引：每个病案最多一条 type='main'
CREATE UNIQUE INDEX uq_mrd_one_main_per_record
    ON medical_record_diagnosis (record_id)
    WHERE diag_type = 'main';
