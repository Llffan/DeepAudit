-- ============================================================================
-- V8: 门(急)诊诊断扩展两字段
--
-- 病案首页中"门(急)诊诊断"行实际上是 4 列：诊断名称 / 疾病编码 / 入院情况
-- / 入院后确诊日期。V6 只建了前两列，V8 补齐后两列。
--
-- 入院情况(outpatient_admission_condition) 与 V7 子表的 admission_condition
-- 同字典（HQMS RC014：有 / 临床未确定 / 情况不明 / 无），但属于门急诊诊断
-- 这一行的属性，不能合并到诊断子表。
--
-- 入院后确诊日期是新生独立字段，常见于入院诊断不明、住院期间检查后确诊的
-- 病例。
-- ============================================================================

ALTER TABLE medical_record_main
    ADD COLUMN outpatient_admission_condition  VARCHAR(20),
    ADD COLUMN confirmed_after_admission_date  DATE;
