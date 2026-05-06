-- ============================================================================
-- V13__widen_icd_dict_version.sql
--
-- V1 把 icd_dict.version 定为 VARCHAR(20)，但实际 JSONL 字典里用的版本号
--   "national_clinical_v3.0"  (23 字符)
--   "national_clinical_v2.0"  (23 字符)
-- 都超过 20 字符，导致 IcdDictLoader 启动灌字典时每行报
--   ERROR: value too long for type character varying(20)
-- 0 行入库。
--
-- 拓宽到 64 以留足未来余量（"medical_insurance_v2024_q4" 这种命名也能装下）。
-- ALTER COLUMN TYPE 在 PostgreSQL 上对扩长是即时操作，不重写表。
-- ============================================================================

ALTER TABLE icd_dict ALTER COLUMN version TYPE VARCHAR(64);
